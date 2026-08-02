package com.ebstudy.portal.board.gallery;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.Attachment;
import com.ebstudy.portal.board.common.AttachmentRepository;
import com.ebstudy.portal.board.common.AttachmentStorage;
import com.ebstudy.portal.board.common.AttachmentValidator;
import com.ebstudy.portal.board.common.BoardAccessGuard;
import com.ebstudy.portal.board.common.BoardSearchCriteria;
import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.Category;
import com.ebstudy.portal.board.common.CategoryRepository;
import com.ebstudy.portal.board.common.NewBadgePolicy;
import com.ebstudy.portal.board.common.PageResponse;
import com.ebstudy.portal.board.common.Post;
import com.ebstudy.portal.board.common.PostNumbering;
import com.ebstudy.portal.board.common.PostRepository;
import com.ebstudy.portal.board.common.PostSpecifications;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import com.ebstudy.portal.user.User;
import com.ebstudy.portal.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 갤러리(005) — 요구사항 5장.
 *
 * <p>갤러리가 다른 게시판과 다른 지점은 하나다: <b>이미지의 순서가 데이터다.</b>
 * 요구사항 5.1 은 "첫 번째 이미지가 썸네일", 5.2 는 "캐러셀"이라고 정했다.
 * 순서가 흔들리면 목록의 썸네일과 상세 첫 장이 달라지므로,
 * 이 서비스는 이미지 집합이 바뀔 때마다 {@code sort_order} 를 <b>0..n-1 로 다시 매긴다</b>
 * (구멍이 생기면 "몇 번째"라는 말이 의미를 잃는다).
 *
 * <p>파일과 DB 를 함께 바꾸는 곳이라 <b>트랜잭션 경계 밖의 부작용</b>을 조심해서 다룬다:
 * 새로 저장한 파일은 <i>롤백되면</i> 지우고, 지워야 할 파일은 <i>커밋된 뒤에</i> 지운다
 * ({@link #deleteFilesOnRollback} · {@link #deleteFilesAfterCommit}).
 * 반대로 하면 "글은 남았는데 이미지가 사라진" 상태가 만들어진다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryService {

    private static final BoardType BOARD = BoardType.GALLERY;

    /** 요구사항 1.2 "제목 100자 미만" — V6 {@code title VARCHAR(99)} 와 같은 값이다. */
    private static final int TITLE_MAX = 99;
    /** 요구사항 1.2 "내용 4000자 미만" — V6 {@code content VARCHAR(3999)}. */
    private static final int CONTENT_MAX = 3999;
    /** 요구사항 5.1 "내용 일부". 기획에 길이 규정이 없어 정했다(보고 대상). */
    private static final int EXCERPT_MAX = 120;
    /** 갤러리 글은 이미지가 본체다 — 0장이면 목록 카드에 썸네일이 없다(판단, 보고 대상). */
    private static final int MIN_IMAGES = 1;
    /** 메인 페이지(요구사항 2장) 갤러리 영역은 3개다. */
    private static final int DEFAULT_LATEST = 3;
    private static final int MAX_LATEST = 20;

    private final PostRepository posts;
    private final CategoryRepository categories;
    private final AttachmentRepository attachments;
    private final AttachmentStorage storage;
    private final AttachmentValidator attachmentValidator;
    private final BoardAccessGuard guard;
    private final NewBadgePolicy newBadgePolicy;
    private final UserRepository users;

    // ── 조회 ────────────────────────────────────────────────

    /** 요구사항 5.1 — 카드형 목록. 검색·정렬·페이징 규칙은 {@link BoardSearchCriteria} 가 이미 검증했다. */
    @Transactional(readOnly = true)
    public PageResponse<GalleryCardResponse> list(BoardSearchCriteria criteria,
            AuthenticatedUser principal) {
        Page<Post> page = search(criteria, principal);
        OffsetDateTime now = OffsetDateTime.now();
        Map<Long, List<Attachment>> imagesByPost = imagesOf(page.getContent());
        long[] numbers = numbersOf(page);

        List<GalleryCardResponse> items = new ArrayList<>(page.getNumberOfElements());
        for (int i = 0; i < page.getContent().size(); i++) {
            Post post = page.getContent().get(i);
            items.add(GalleryCardResponse.of(numbers[i], post,
                    imagesByPost.getOrDefault(post.getId(), List.of()),
                    excerpt(post.getContent()), isNew(post, now)));
        }
        return PageResponse.of(page, items);
    }

    /** 요구사항 5.4 — 관리자 목록. 조건 자체는 사용자 목록과 같고 보여주는 열만 다르다. */
    @Transactional(readOnly = true)
    public PageResponse<GalleryAdminRowResponse> listForAdmin(BoardSearchCriteria criteria,
            AuthenticatedUser principal) {
        guard.requireAdmin(principal);
        Page<Post> page = search(criteria, principal);
        OffsetDateTime now = OffsetDateTime.now();
        Map<Long, List<Attachment>> imagesByPost = imagesOf(page.getContent());
        long[] numbers = numbersOf(page);

        List<GalleryAdminRowResponse> items = new ArrayList<>(page.getNumberOfElements());
        for (int i = 0; i < page.getContent().size(); i++) {
            Post post = page.getContent().get(i);
            items.add(GalleryAdminRowResponse.of(numbers[i], post,
                    imagesByPost.getOrDefault(post.getId(), List.of()), isNew(post, now)));
        }
        return PageResponse.of(page, items);
    }

    /** 메인 페이지(요구사항 2장) — 최신 3개 + 썸네일 + 첫 이미지 제외 개수. */
    @Transactional(readOnly = true)
    public List<GalleryCardResponse> latest(Integer limit) {
        int size = limit == null ? DEFAULT_LATEST : limit;
        if (size < 1 || size > MAX_LATEST) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
        List<Post> content = posts.findByBoardTypeOrderByCreatedAtDescIdDesc(BOARD,
                PageRequest.of(0, size));
        OffsetDateTime now = OffsetDateTime.now();
        Map<Long, List<Attachment>> imagesByPost = imagesOf(content);
        // 메인은 페이지가 없다 → 번호의 기준은 게시판 전체 수다(목록의 1페이지와 같은 번호가 나온다)
        long total = posts.countByBoardType(BOARD);

        List<GalleryCardResponse> items = new ArrayList<>(content.size());
        for (int i = 0; i < content.size(); i++) {
            Post post = content.get(i);
            items.add(GalleryCardResponse.of(total - i, post,
                    imagesByPost.getOrDefault(post.getId(), List.of()),
                    excerpt(post.getContent()), isNew(post, now)));
        }
        return items;
    }

    /** 요구사항 1.1 — 목록 화면의 분류 드롭다운. 활성만 내려간다(7.2). */
    @Transactional(readOnly = true)
    public List<GalleryCategoryResponse> categories() {
        return categories.findByBoardTypeAndActiveTrueOrderBySortOrderAscIdAsc(BOARD).stream()
                .map(GalleryCategoryResponse::of)
                .toList();
    }

    /**
     * 요구사항 5.2 상세 — 조회수가 오른다(1.4).
     *
     * <p>{@code readOnly} 가 아닌 이유가 조회수다. 그리고 DTO 를 <b>먼저 다 만든 뒤</b>
     * 증가시키는 것은 실수를 피하기 위한 순서다 —
     * {@code PostRepository.increaseViewCount} 는 {@code clearAutomatically = true} 라
     * 호출 즉시 영속성 컨텍스트가 비워지고, 그 뒤에 지연 로딩(분류·등록자·이미지)을 건드리면
     * 쿼리가 다시 나가거나 준영속 예외가 된다.
     */
    @Transactional
    public GalleryDetailResponse read(Long id, AuthenticatedUser principal) {
        Post post = find(id);
        GalleryDetailResponse response = detail(post, principal, post.getViewCount() + 1);
        posts.increaseViewCount(id);
        return response;
    }

    /**
     * 관리자 상세.
     *
     * <p>★ <b>조회수를 올리지 않는다.</b> 요구사항 1.4 의 "상세 조회"는 사용자 화면의 열람이고,
     * 관리자가 관리 목적으로 열어 본 것까지 세면 조회수가 통계로서 못 쓰게 된다.
     * (기획에 없는 판단이라 보고 대상이다.)
     */
    @Transactional(readOnly = true)
    public GalleryDetailResponse readForAdmin(Long id, AuthenticatedUser principal) {
        guard.requireAdmin(principal);
        Post post = find(id);
        return detail(post, principal, post.getViewCount());
    }

    /** 요구사항 5.2 캐러셀·5.1 썸네일이 실제로 읽어 가는 바이너리. */
    @Transactional(readOnly = true)
    public GalleryImageFile readImage(Long postId, Long imageId) {
        // 글을 먼저 확인한다 — 첨부 id 만으로 찾으면 자유게시판 첨부를 갤러리 경로로 꺼낼 수 있다
        Post post = find(postId);
        Attachment attachment = attachments.findById(imageId)
                .filter(candidate -> candidate.belongsTo(post.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.ATTACHMENT_NOT_FOUND));
        return GalleryImageFile.of(attachment, storage.load(attachment.getStoredPath()));
    }

    // ── 등록 · 수정 · 삭제 ─────────────────────────────────────

    /** 요구사항 5.3 등록. 로그인 사용자면 누구나(0장 "글 등록 주체"). */
    @Transactional
    public Long create(AuthenticatedUser principal, GalleryWriteRequest request,
            List<MultipartFile> images) {
        guard.requireCanWrite(BOARD, principal);

        String title = requireTitle(request.title());
        String content = requireContent(request.content());
        Category category = writableCategory(request.categoryId());
        List<MultipartFile> files = uploaded(images);
        requireImageCount(files.size());
        attachmentValidator.validate(BOARD, files, 0);

        OffsetDateTime now = OffsetDateTime.now();
        Post post = posts.save(Post.gallery(category, author(principal), title, content, now));
        appendImages(post, files, 0, now);
        return post.getId();
    }

    /**
     * 요구사항 5.3 수정 — 분류·제목·내용과 <b>이미지 집합·순서를 한 번에</b> 바꾼다.
     *
     * <p>이미지 삭제·추가·순서 변경을 각각의 엔드포인트로 나누지 않은 이유는 요구사항 1.2 다:
     * 수정 화면의 <i>"취소"</i> 는 <b>아무것도 바뀌지 않았어야</b> 한다. 개별 삭제를 즉시
     * 반영해 버리면 사용자가 취소해도 이미지는 이미 사라진 뒤다.
     *
     * @param keepImageIds 남길 기존 이미지 id 를 <b>보여줄 순서대로</b>. 여기 없는 기존 이미지는
     *                     지워진다. {@code null} 이면 "지금 순서 그대로 전부 유지" 다
     *                     (화면이 이미지 칸을 건드리지 않은 경우)
     * @param newImages    뒤에 이어 붙일 새 이미지
     */
    @Transactional
    public void update(Long id, AuthenticatedUser principal, GalleryWriteRequest request,
            List<Long> keepImageIds, List<MultipartFile> newImages) {
        Post post = find(id);
        guard.requireOwner(principal, authorIdOf(post));

        String title = requireTitle(request.title());
        String content = requireContent(request.content());
        Category category = writableCategory(request.categoryId());

        List<Attachment> existing = attachments.findByPostIdOrderBySortOrderAscIdAsc(id);
        List<Attachment> keep = resolveKeep(existing, keepImageIds);
        List<Attachment> removed = existing.stream().filter(image -> !keep.contains(image)).toList();
        List<MultipartFile> files = uploaded(newImages);

        // 남길 것 + 새로 올릴 것의 <b>합계</b>로 본다 — 20개짜리 글에 20개를 더 올리지 못하게
        attachmentValidator.validate(BOARD, files, keep.size());
        requireImageCount(keep.size() + files.size());

        OffsetDateTime now = OffsetDateTime.now();
        post.changeCategory(category, now);
        post.updateContent(title, content, now);

        if (!removed.isEmpty()) {
            attachments.deleteAll(removed);
            // 행을 먼저 지워 둬야 남은 것들의 sort_order 재배치가 뒤엉키지 않는다
            attachments.flush();
            deleteFilesAfterCommit(storedPaths(removed));
        }
        int order = 0;
        for (Attachment image : keep) {
            image.reorder(order++);
        }
        appendImages(post, files, order, now);
    }

    /** 요구사항 1.3 — 본인 글만. 관리자라도 사용자 화면에서는 남의 글을 지우지 못한다. */
    @Transactional
    public void delete(Long id, AuthenticatedUser principal) {
        Post post = find(id);
        guard.requireOwner(principal, authorIdOf(post));
        remove(post);
    }

    /** 관리 화면의 삭제 — 요구사항 7.1(관리자 전용). */
    @Transactional
    public void deleteByAdmin(Long id, AuthenticatedUser principal) {
        guard.requireAdmin(principal);
        remove(find(id));
    }

    // ── 내부 ────────────────────────────────────────────────

    private Page<Post> search(BoardSearchCriteria criteria, AuthenticatedUser principal) {
        Long viewerId = principal == null ? null : principal.userId();
        // ⚠️ 분류·등록자는 지연 로딩이라 카드 매핑에서 글마다 조회가 나간다(한 페이지 최대 50행).
        //    지금은 정확성을 택하고 그대로 둔다. 방아쇠: 목록 응답 시간이 문제가 되는 시점 —
        //    그때 이 패키지에 @EntityGraph 를 얹은 전용 리포지토리를 두거나 배치 페치를 켠다
        return posts.findAll(PostSpecifications.search(criteria, viewerId), criteria.toPageable());
    }

    /**
     * 요구사항 1.1 "전체 게시글 수 기준 역순".
     *
     * <p>기준값으로 <b>현재 검색 결과의 전체 수</b>를 쓴다. 게시판 전체 수를 쓰면 3건짜리
     * 검색 결과에 100·99·98 이 찍혀 페이지 수와 번호가 서로 어긋난 화면이 된다.
     * 요구사항 문구는 둘 중 어느 쪽인지 단정하지 않는다 —
     * <b>게시판 4종이 같은 쪽을 써야 하므로</b> 이 선택은 보고 대상이다.
     */
    private long[] numbersOf(Page<Post> page) {
        return PostNumbering.displayNumbers(page.getTotalElements(), page.getNumber(),
                page.getSize(), page.getNumberOfElements());
    }

    private GalleryDetailResponse detail(Post post, AuthenticatedUser principal, long viewCount) {
        List<Attachment> images = attachments.findByPostIdOrderBySortOrderAscIdAsc(post.getId());
        boolean owned = guard.isOwner(principal, authorIdOf(post));
        return GalleryDetailResponse.of(post, images, viewCount,
                isNew(post, OffsetDateTime.now()), owned);
    }

    /** 다른 게시판의 글 id 로 갤러리 API 를 통과하는 것을 막는다. */
    private Post find(Long id) {
        if (id == null) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }
        return posts.findByIdAndBoardType(id, BOARD)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }

    /** 지연 로딩 프록시의 {@code id} 는 조회 없이 읽힌다 — 소유자 판정 하나에 등록자를 통째로 불러오지 않는다. */
    private Long authorIdOf(Post post) {
        return post.getAuthor() == null ? null : post.getAuthor().getId();
    }

    private User author(AuthenticatedUser principal) {
        // 토큰은 무상태라 계정이 사라진 뒤에도 잠시 유효하다 → 글의 주인은 실제 행으로 확인한다
        return users.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED));
    }

    /**
     * 새 글·수정에 쓸 수 있는 분류인가.
     *
     * <p>비활성 분류를 거부하는 것이 핵심이다(요구사항 7.2). 관리자가 내린 분류가
     * 드롭다운에서는 사라져도 요청 본문에는 그대로 넣을 수 있다 —
     * 화면에서 안 보이는 것은 검증이 아니다.
     */
    private Category writableCategory(Long categoryId) {
        if (categoryId == null) {
            throw new ApiException(ErrorCode.CATEGORY_REQUIRED);
        }
        Category category = categories.findByIdAndBoardType(categoryId, BOARD)
                .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));
        if (!category.isActive()) {
            throw new ApiException(ErrorCode.CATEGORY_NOT_SELECTABLE);
        }
        return category;
    }

    /**
     * 남길 이미지를 <b>요청한 순서대로</b> 정렬해 돌려준다.
     *
     * <p>id 를 하나씩 확인하는 이유: 남의 글 첨부 id 를 섞어 보내면 그 첨부가 이 글로
     * 옮겨 붙거나(순서 재배치) 남의 파일이 지워질 수 있다. 목록에 없는 id 는 즉시 거부한다.
     */
    private List<Attachment> resolveKeep(List<Attachment> existing, List<Long> keepImageIds) {
        if (keepImageIds == null) {
            return List.copyOf(existing);
        }
        Map<Long, Attachment> byId = existing.stream()
                .collect(Collectors.toMap(Attachment::getId, image -> image, (a, b) -> a,
                        LinkedHashMap::new));
        Set<Long> seen = new LinkedHashSet<>();
        List<Attachment> keep = new ArrayList<>(keepImageIds.size());
        for (Long imageId : keepImageIds) {
            Attachment image = imageId == null ? null : byId.get(imageId);
            if (image == null || !seen.add(imageId)) {
                // 없는 id · 남의 첨부 · 같은 id 중복 — 셋 다 순서를 신뢰할 수 없게 만든다
                throw new ApiException(ErrorCode.REQUEST_INVALID);
            }
            keep.add(image);
        }
        return keep;
    }

    /** 브라우저는 파일을 고르지 않아도 빈 파트를 보낸다 — 검증 전에 걸러야 "빈 파일" 오류가 되지 않는다. */
    private List<MultipartFile> uploaded(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream().filter(file -> file != null && !file.isEmpty()).toList();
    }

    private void requireImageCount(int count) {
        if (count < MIN_IMAGES) {
            throw new ApiException(ErrorCode.GALLERY_IMAGE_REQUIRED);
        }
    }

    /** 저장 → 행 추가. 저장한 파일은 롤백 시 지우도록 예약해 둔다. */
    private void appendImages(Post post, List<MultipartFile> files, int startOrder,
            OffsetDateTime now) {
        if (files.isEmpty()) {
            return;
        }
        List<AttachmentStorage.StoredFile> stored = new ArrayList<>(files.size());
        List<Attachment> rows = new ArrayList<>(files.size());
        int order = startOrder;
        for (MultipartFile file : files) {
            AttachmentStorage.StoredFile saved = storage.store(BOARD, file);
            stored.add(saved);
            rows.add(Attachment.create(post, saved, order++, now));
        }
        // 저장은 트랜잭션 밖의 일이다 — 뒤에서 롤백되면 주인 없는 파일만 남는다
        deleteFilesOnRollback(stored.stream().map(AttachmentStorage.StoredFile::storedPath).toList());
        attachments.saveAll(rows);
    }

    private void remove(Post post) {
        // 행은 V8 의 ON DELETE CASCADE 로 사라지지만 <b>디스크 파일은 서버가 지운다</b>.
        // 그래서 지우기 전에 경로를 먼저 읽어 둔다 — 단 <b>엔티티가 아니라 경로만</b> 읽는다.
        // 엔티티로 읽으면 아래 posts.delete(post) 가 커밋에서 깨진다
        // (AttachmentRepository.findStoredPathsByPostId 주석 참조)
        List<String> paths = attachments.findStoredPathsByPostId(post.getId());
        posts.delete(post);
        deleteFilesAfterCommit(paths);
    }

    private List<String> storedPaths(List<Attachment> images) {
        return images.stream().map(Attachment::getStoredPath).toList();
    }

    /**
     * 커밋된 뒤에 파일을 지운다.
     *
     * <p>먼저 지우면 트랜잭션이 롤백됐을 때 <b>글은 그대로인데 이미지만 사라진다</b> —
     * 사용자가 되돌릴 방법이 없는 손실이다. 반대(파일이 남는 쪽)는 디스크 낭비로 끝난다.
     */
    private void deleteFilesAfterCommit(List<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.deleteAll(paths);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.deleteAll(paths);
            }
        });
    }

    /** 롤백됐을 때만 지운다 — 커밋됐다면 그 파일들은 방금 만들어진 정상 첨부다. */
    private void deleteFilesOnRollback(List<String> paths) {
        if (paths.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    log.warn("갤러리 저장이 롤백됐다 — 올린 파일을 지운다 count={}", paths.size());
                    storage.deleteAll(paths);
                }
            }
        });
    }

    private Map<Long, List<Attachment>> imagesOf(List<Post> content) {
        if (content.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = content.stream().map(Post::getId).toList();
        // ★ 썸네일과 개수를 행마다 조회하면 N+1 이다. 한 번에 읽어 글별로 나눈다
        return attachments.findByPostIdInOrderByPostIdAscSortOrderAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(image -> image.getPost().getId(),
                        LinkedHashMap::new, Collectors.toList()));
    }

    private boolean isNew(Post post, OffsetDateTime now) {
        return newBadgePolicy.isNew(BOARD, post.getCreatedAt(), now);
    }

    private String requireTitle(String raw) {
        String title = raw == null ? "" : raw.trim();
        if (title.isEmpty()) {
            throw new ApiException(ErrorCode.POST_TITLE_REQUIRED);
        }
        if (charCount(title) > TITLE_MAX) {
            throw new ApiException(ErrorCode.POST_TITLE_LENGTH_INVALID);
        }
        return title;
    }

    private String requireContent(String raw) {
        // 내용은 <b>trim 하지 않는다</b> — 들여쓰기와 줄바꿈이 본문의 일부다.
        // 다만 "공백만 있는 본문"은 빈 것으로 본다(V6 ck_posts_content_not_blank 와 같은 판정)
        String content = raw == null ? "" : raw;
        if (content.isBlank()) {
            throw new ApiException(ErrorCode.POST_CONTENT_REQUIRED);
        }
        if (charCount(content) > CONTENT_MAX) {
            throw new ApiException(ErrorCode.POST_CONTENT_LENGTH_INVALID);
        }
        return content;
    }

    /** 길이는 <b>문자 수</b>로 센다 — 한글·이모지 허용({@code SignupService.charCount} 와 같은 규칙). */
    private static int charCount(String value) {
        return value.codePointCount(0, value.length());
    }

    /** 요구사항 5.1 "내용 일부" — 줄바꿈을 접어 카드 한 줄에 들어가게 한다. */
    private static String excerpt(String content) {
        if (content == null) {
            return "";
        }
        String flattened = content.replaceAll("\\s+", " ").trim();
        if (charCount(flattened) <= EXCERPT_MAX) {
            return flattened;
        }
        // offsetByCodePoints — 이모지 한 글자를 반으로 자르면 깨진 문자가 나간다
        return flattened.substring(0, flattened.offsetByCodePoints(0, EXCERPT_MAX)) + "…";
    }
}
