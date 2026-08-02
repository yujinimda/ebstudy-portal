package com.ebstudy.portal.board.free;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.BoardAccessGuard;
import com.ebstudy.portal.board.common.BoardSearchCriteria;
import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.Category;
import com.ebstudy.portal.board.common.CategoryRepository;
import com.ebstudy.portal.board.common.CommentRepository;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자유게시판 — 요구사항 4장.
 *
 * <p>게시판 4종 중 유일하게 <b>댓글과 첨부가 둘 다</b> 있다. 그 둘은 각각
 * {@link FreeCommentService} · {@link FreeAttachmentService} 가 맡고 이 클래스는
 * <b>글의 일생</b>(목록·상세·등록·수정·삭제)과 권한 판정만 본다.
 *
 * <p>지키는 원칙 세 가지:
 * <ol>
 *   <li><b>검증은 서버에서</b>(요구사항 1.2). 화면을 거치지 않은 직접 호출도 같은 코드로 거부된다</li>
 *   <li><b>권한도 서버에서</b>(요구사항 1.3). 버튼을 숨기는 것은 검증이 아니다</li>
 *   <li><b>목록에서 N+1 을 내지 않는다</b>(요구사항 4.1 이 목록에 댓글 수·첨부 아이콘을 요구한다).
 *       한 페이지에 나가는 쿼리는 <b>4번으로 고정</b>이다 —
 *       ① 목록(작성자·분류 페치 조인) ② 전체 개수 ③ 댓글 수 집계 ④ 첨부 수 집계.
 *       50건이든 10건이든 같다</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FreeBoardService {

    private static final BoardType BOARD = BoardType.FREE;
    /** 요구사항 1.2 "제목 100자 미만" → 99. V6 {@code posts.title VARCHAR(99)} 와 같은 값. */
    private static final int TITLE_MAX = 99;
    /** 요구사항 1.2 "내용 4000자 미만" → 3999. */
    private static final int CONTENT_MAX = 3999;

    private final FreePostRepository posts;
    /** 조회수 UPDATE 전용 — 공통 리포지토리의 {@code @Modifying} 쿼리를 쓴다. */
    private final PostRepository postCommands;
    private final CategoryRepository categories;
    private final CommentRepository commentCounts;
    private final UserRepository users;
    private final FreeAttachmentService attachmentService;
    private final FreeCommentService commentService;
    private final BoardAccessGuard guard;
    private final NewBadgePolicy newBadge;

    // ── 목록 ────────────────────────────────────────────────

    /** 요구사항 1.1 · 4.1 — 기간·분류·검색어·정렬·페이징. */
    @Transactional(readOnly = true)
    public PageResponse<FreePostListItem> list(FreePostListQuery query,
            AuthenticatedUser principal) {
        OffsetDateTime now = OffsetDateTime.now();
        Long viewerId = principal == null ? null : principal.userId();

        // 검증·기본값은 전부 여기 한 번. 이 뒤로 흐르는 값은 검증을 통과한 것뿐이다
        BoardSearchCriteria criteria = BoardSearchCriteria.of(BOARD,
                startOfDay(query.from()), endOfDay(query.to()),
                query.categoryId(), query.keyword(),
                // 자유게시판에는 "나의 글만 보기" 가 없다(요구사항 6.1 은 문의게시판 전용)
                null, viewerId,
                query.page(), query.size(), query.sort(), query.direction(), now);

        Page<Post> page = posts.findAll(PostSpecifications.search(criteria, viewerId),
                criteria.toPageable());

        List<Post> content = page.getContent();
        List<Long> postIds = content.stream().map(Post::getId).toList();
        // ★ 두 줄이 N+1 방어의 전부다. 행마다 세면 한 페이지에 쿼리 100번이 나간다
        Map<Long, Long> commentCountByPost = commentCounts.countsByPostIds(postIds);
        Map<Long, Long> attachmentCountByPost = attachmentService.countsByPostIds(postIds);

        long[] numbers = PostNumbering.displayNumbers(page.getTotalElements(), criteria.page(),
                criteria.size(), content.size());

        List<FreePostListItem> items = new ArrayList<>(content.size());
        for (int i = 0; i < content.size(); i++) {
            Post post = content.get(i);
            items.add(FreePostListItem.of(post, numbers[i],
                    commentCountByPost.getOrDefault(post.getId(), 0L),
                    attachmentCountByPost.getOrDefault(post.getId(), 0L),
                    newBadge.isNew(BOARD, post.getCreatedAt(), now)));
        }
        return PageResponse.of(page, items);
    }

    /**
     * 메인 페이지(요구사항 2장) — 자유게시판 최신 5개 + 댓글 수 · 첨부 아이콘.
     *
     * <p><b>목록 API 를 재사용하지 않는 이유</b>: 목록은 요구사항 1.1 에 따라 기간이
     * <b>기본 1달</b>이다. 메인은 기간 개념이 없으므로 그대로 쓰면 한 달 넘게 글이 없는
     * 게시판이 <b>빈 칸</b>으로 보인다. 사용자에게는 고장으로 읽힌다.
     *
     * <p>댓글 수·첨부 수는 목록과 <b>같은 집계 쿼리</b>를 쓴다 — 5줄이라도 행마다 세면
     * N+1 이고, 메인은 4개 게시판이 동시에 뜨는 화면이라 그 비용이 4배가 된다.
     */
    @Transactional(readOnly = true)
    public List<FreePostListItem> latest(int limit) {
        if (limit < 1 || limit > 20) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<Post> content = postCommands.findByBoardTypeOrderByCreatedAtDescIdDesc(BOARD,
                PageRequest.of(0, limit));
        List<Long> postIds = content.stream().map(Post::getId).toList();
        Map<Long, Long> commentCountByPost = commentCounts.countsByPostIds(postIds);
        Map<Long, Long> attachmentCountByPost = attachmentService.countsByPostIds(postIds);

        // 메인은 페이지가 없다 → 번호 기준은 게시판 전체 수(목록 1페이지와 같은 번호가 나온다)
        long[] numbers = PostNumbering.displayNumbers(postCommands.countByBoardType(BOARD),
                0, limit, content.size());

        List<FreePostListItem> items = new ArrayList<>(content.size());
        for (int i = 0; i < content.size(); i++) {
            Post post = content.get(i);
            items.add(FreePostListItem.of(post, numbers[i],
                    commentCountByPost.getOrDefault(post.getId(), 0L),
                    attachmentCountByPost.getOrDefault(post.getId(), 0L),
                    newBadge.isNew(BOARD, post.getCreatedAt(), now)));
        }
        return items;
    }

    /** 요구사항 1.1 분류 드롭다운 — 사용 중인 분류만(요구사항 7.2). */
    @Transactional(readOnly = true)
    public List<FreeCategoryItem> categories() {
        return categories.findByBoardTypeAndActiveTrueOrderBySortOrderAscIdAsc(BOARD).stream()
                .map(FreeCategoryItem::of)
                .toList();
    }

    // ── 상세 ────────────────────────────────────────────────

    /**
     * 요구사항 4.2 상세 + 1.4 조회수 증가.
     *
     * <p>★ 순서가 중요하다. {@code increaseViewCount} 는 {@code clearAutomatically = true} 라
     * <b>영속성 컨텍스트를 비운다</b> — 그 뒤에 엔티티를 읽으면 detach 된 값이거나 다시 쿼리가 나간다.
     * 그래서 <b>응답을 다 만든 뒤 마지막에</b> 증가시키고, 화면에 보일 조회수는 {@code +1} 해서 담는다
     * (자기가 연 조회가 반영되지 않으면 사용자는 증가가 안 된다고 본다).
     *
     * <p>DB 에서 더하는 UPDATE 를 쓰는 이유는 동시성이다. 읽은 값에 1을 더해 쓰면 같은 순간
     * 열린 두 요청 중 하나가 묻힌다({@code Post.increaseViewCount} 주석).
     */
    @Transactional
    public FreePostDetail detail(Long postId, AuthenticatedUser principal) {
        Post post = requirePost(postId);
        FreePostDetail detail = toDetail(post, principal, post.getViewCount() + 1);
        postCommands.increaseViewCount(postId);
        return detail;
    }

    /**
     * 관리 화면 상세(요구사항 7.1) — <b>조회수를 올리지 않는다</b>.
     * 관리자가 글을 확인할 때마다 통계가 부풀면 조회수가 지표로 쓸모없어진다.
     */
    @Transactional(readOnly = true)
    public FreePostDetail detailForAdmin(Long postId, AuthenticatedUser principal) {
        guard.requireAdmin(principal);
        Post post = requirePost(postId);
        return toDetail(post, principal, post.getViewCount());
    }

    private FreePostDetail toDetail(Post post, AuthenticatedUser principal, long viewCount) {
        boolean owner = guard.isOwner(principal, post.getAuthor().getId());
        return FreePostDetail.of(post, viewCount,
                attachmentService.itemsOf(post.getId()),
                commentService.itemsOf(post.getId(), principal),
                // 요구사항 1.3 — 사용자 화면에서는 관리자에게도 남의 글 수정·삭제를 열지 않는다.
                // 관리자의 삭제는 관리 화면(/api/admin/free-posts)의 별도 동작이다
                owner, owner,
                principal != null && principal.userId() != null);
    }

    // ── 등록 · 수정 · 삭제 ───────────────────────────────────

    /** 요구사항 4.3 등록. 자유게시판은 로그인만 하면 쓸 수 있다(요구사항 0장). */
    @Transactional
    public Long create(AuthenticatedUser principal, FreePostWriteRequest request) {
        guard.requireCanWrite(BOARD, principal);
        OffsetDateTime now = OffsetDateTime.now();

        String title = requireTitle(request.title());
        String content = requireContent(request.content());
        Category category = requireCategory(request.categoryId(), null);
        User author = users.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED));

        Post post = posts.save(Post.free(category, author, title, content, now));
        attachmentService.store(post, request.files(), 0, now);
        log.info("자유게시판 글 등록 postId={} authorId={}", post.getId(), author.getId());
        return post.getId();
    }

    /**
     * 요구사항 4.3 수정 — <b>본인만</b>(요구사항 1.3).
     *
     * <p>기존 첨부 삭제 → 순서 정리 → 새 첨부 추가 순서인 이유: 개수 한도(5개)는 <b>최종 상태</b>
     * 기준이다. 지우기 전에 세면 "3개 지우고 3개 올리기" 가 8개로 계산되어 막힌다.
     */
    @Transactional
    public void update(AuthenticatedUser principal, Long postId, FreePostWriteRequest request) {
        Post post = requirePost(postId);
        guard.requireOwner(principal, post.getAuthor().getId());
        OffsetDateTime now = OffsetDateTime.now();

        String title = requireTitle(request.title());
        String content = requireContent(request.content());
        Category category = requireCategory(request.categoryId(), post.getCategory());

        post.updateContent(title, content, now);
        post.changeCategory(category, now);

        attachmentService.remove(post, request.removeAttachmentIds());
        int remaining = attachmentService.renumber(postId);
        attachmentService.store(post, request.files(), remaining, now);
        log.info("자유게시판 글 수정 postId={} userId={}", postId, principal.userId());
    }

    /** 요구사항 4.2 삭제 — 본인만. */
    @Transactional
    public void delete(AuthenticatedUser principal, Long postId) {
        Post post = requirePost(postId);
        guard.requireOwner(principal, post.getAuthor().getId());
        deleteInternal(post);
        log.info("자유게시판 글 삭제 postId={} userId={}", postId, principal.userId());
    }

    /** 관리 화면 삭제(요구사항 7.1) — 관리자는 남의 글도 지운다. 사용자 경로와 분리한 이유는 위 참조. */
    @Transactional
    public void deleteByAdmin(AuthenticatedUser principal, Long postId) {
        guard.requireAdmin(principal);
        deleteInternal(requirePost(postId));
        log.info("자유게시판 글 관리자 삭제 postId={} adminId={}", postId, principal.userId());
    }

    private void deleteInternal(Post post) {
        // 댓글·첨부 <b>행</b>은 V7·V8 의 ON DELETE CASCADE 가 지운다.
        // 디스크 파일은 DB 가 모르므로 경로를 먼저 읽어 두고 커밋 뒤에 지운다
        attachmentService.removeFilesOf(post.getId());
        posts.delete(post);
    }

    // ── 검증 ────────────────────────────────────────────────

    private Post requirePost(Long postId) {
        if (postId == null) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }
        return posts.findWithAuthorAndCategoryByIdAndBoardType(postId, BOARD)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }

    private String requireTitle(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new ApiException(ErrorCode.POST_TITLE_REQUIRED);
        }
        if (charCount(value) > TITLE_MAX) {
            throw new ApiException(ErrorCode.POST_TITLE_LENGTH_INVALID);
        }
        return value;
    }

    private String requireContent(String raw) {
        // 앞뒤 공백만 있는 내용은 빈 것으로 본다 — V6 의 ck_posts_content_not_blank 와 같은 판정
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new ApiException(ErrorCode.POST_CONTENT_REQUIRED);
        }
        if (charCount(value) > CONTENT_MAX) {
            throw new ApiException(ErrorCode.POST_CONTENT_LENGTH_INVALID);
        }
        return value;
    }

    /**
     * 요구사항 4.3 — 분류는 필수다.
     *
     * @param current 수정 중인 글이 <b>이미 쓰고 있던</b> 분류. 그 분류가 그사이 비활성으로
     *                내려갔다면(요구사항 7.2) 그대로 두는 수정은 허용해야 한다 —
     *                막으면 사용자는 분류를 바꾸지 않는 한 오타 하나도 고칠 수 없다
     */
    private Category requireCategory(Long categoryId, Category current) {
        if (categoryId == null) {
            throw new ApiException(ErrorCode.CATEGORY_REQUIRED);
        }
        Category category = categories.findByIdAndBoardType(categoryId, BOARD)
                .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));
        boolean keepingCurrent = current != null && category.getId().equals(current.getId());
        if (!category.isActive() && !keepingCurrent) {
            throw new ApiException(ErrorCode.CATEGORY_NOT_SELECTABLE);
        }
        return category;
    }

    /** 길이는 <b>문자 수</b>로 센다(001 {@code SignupService.charCount} 와 같은 규칙). */
    private static int charCount(String value) {
        return value.codePointCount(0, value.length());
    }

    // ── 기간 검색의 날짜 → 시각 변환 ──────────────────────────

    /**
     * "이 날짜부터" 는 그날 00:00:00 이다.
     *
     * <p>기준 시간대는 <b>서버</b>다. 사용자 기기의 시간대를 쓰면 같은 검색이 사람마다 다른
     * 결과를 낸다. 시간대를 화면이 보내야 할 만큼 정밀해지면 그때 파라미터로 받는다.
     */
    private static OffsetDateTime startOfDay(LocalDate date) {
        return date == null ? null
                : date.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }

    /**
     * "이 날짜까지" 는 그날 <b>23:59:59.999999999</b> 다.
     *
     * <p>00:00 으로 두면 {@code between} 이 양끝 포함이어도 <b>그날 쓴 글이 전부 빠진다</b> —
     * 사용자가 가장 흔히 겪는 "오늘 쓴 글이 검색에 안 나온다" 가 이것이다.
     */
    private static OffsetDateTime endOfDay(LocalDate date) {
        return date == null ? null
                : date.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
