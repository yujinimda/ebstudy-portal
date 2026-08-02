package com.ebstudy.portal.board.notice;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.BoardAccessGuard;
import com.ebstudy.portal.board.common.BoardSearchCriteria;
import com.ebstudy.portal.board.common.Category;
import com.ebstudy.portal.board.common.CategoryRepository;
import com.ebstudy.portal.board.common.PageResponse;
import com.ebstudy.portal.board.common.Post;
import com.ebstudy.portal.board.common.PostRepository;
import com.ebstudy.portal.board.common.PostSpecifications;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import com.ebstudy.portal.user.User;
import com.ebstudy.portal.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지사항 — 관리자. 요구사항 3.3 (목록 · 등록 · 수정 · 삭제).
 *
 * <p>★ <b>모든 메서드가 {@code requireAdmin} 으로 시작한다.</b>
 * {@code SecurityConfig} 가 이미 {@code /api/admin/**} 를 {@code hasRole("ADMIN")} 으로 막고
 * 있지만 그것에만 기대지 않는다 — 나중에 누군가 이 서비스를 다른 경로에서 부르면
 * 경로 규칙은 따라오지 않는다. 권한 판정은 <b>동작을 아는 쪽</b>에 있어야 한다
 * (요구사항 1.3 · 001 {@code FR-019}).
 *
 * <p>요구사항 1.3 은 글 수정·삭제를 "본인 글만" 으로 정했지만 공지사항은 예외다 —
 * 요구사항 3.3 의 관리 화면은 <b>관리자 전체가 공유하는 화면</b>이고, 관리자 A 가 쓴 공지를
 * 관리자 B 가 못 고치면 담당자가 바뀔 때 운영이 멈춘다. 그래서
 * {@code requireOwner} 가 아니라 {@code requireAdmin} 이다. (이 판단은 보고서에 남겼다)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NoticeAdminService {

    /** 요구사항 1.2 "제목 100자 미만". DB 쪽 짝은 {@code posts.title VARCHAR(99)} 다. */
    static final int TITLE_MAX_LENGTH = 99;
    /** 요구사항 1.2 "내용 4000자 미만". DB 쪽 짝은 {@code posts.content VARCHAR(3999)} 다. */
    static final int CONTENT_MAX_LENGTH = 3999;

    private final PostRepository posts;
    private final NoticeRepository noticeQueries;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final NoticeMapper mapper;
    private final NoticeService noticeService;
    private final BoardAccessGuard guard;

    /**
     * 관리 목록 — 요구사항 3.3.
     *
     * <p>사용자 목록과 달리 <b>고정 글을 따로 빼지 않는다.</b> 이유는
     * {@link NoticeAdminListResponse} 에 적었다 — 6번째로 고정된 글을 관리자가
     * 찾지도 풀지도 못하게 되기 때문이다.
     */
    @Transactional(readOnly = true)
    public NoticeAdminListResponse list(BoardSearchCriteria criteria, AuthenticatedUser principal,
            OffsetDateTime now) {
        guard.requireAdmin(principal);

        Page<Post> page = posts.findAll(PostSpecifications.search(criteria, null),
                criteria.toPageable());
        List<Post> items = noticeQueries.withAssociations(page.getContent());

        return new NoticeAdminListResponse(
                PageResponse.of(page, mapper.toListItems(items, page.getTotalElements(),
                        criteria.page(), criteria.size(), true, now)),
                noticeQueries.countByBoardTypeAndPinnedTrue(NoticeService.BOARD),
                NoticeService.PINNED_LIMIT);
    }

    /**
     * 수정 폼이 채울 값 — 요구사항 3.3.
     *
     * <p><b>조회수를 올리지 않는다.</b> 요구사항 1.4 의 "상세 조회" 는 사용자가 글을 읽은
     * 것을 세는 값이다. 관리자가 수정하려고 폼을 여는 것까지 세면 조회수가 운영 행위로 오염된다.
     */
    @Transactional(readOnly = true)
    public NoticeDetailResponse read(Long id, AuthenticatedUser principal) {
        guard.requireAdmin(principal);
        Post post = noticeService.findNotice(id);
        return mapper.toDetail(post, post.getViewCount(), true);
    }

    /** 등록/수정 폼의 분류 선택 — 비활성까지 준다. 이유는 {@link NoticeCategoryResponse} 참고. */
    @Transactional(readOnly = true)
    public List<NoticeCategoryResponse> allCategories(AuthenticatedUser principal) {
        guard.requireAdmin(principal);
        return categories.findByBoardTypeOrderBySortOrderAscIdAsc(NoticeService.BOARD).stream()
                .map(NoticeCategoryResponse::of)
                .toList();
    }

    /** 등록 — 요구사항 3.3 (분류 · 제목 · 내용 · 상단 고정). */
    @Transactional
    public NoticeDetailResponse create(NoticeWriteRequest request, AuthenticatedUser principal,
            OffsetDateTime now) {
        // 요구사항 0장 "글 등록 주체: 공지사항은 관리자만" 을 BoardType 이 알고 있다
        guard.requireCanWrite(NoticeService.BOARD, principal);

        String title = requireTitle(request.title());
        String content = requireContent(request.content());
        Category category = requireCategory(request.categoryId(), null);
        User author = requireAuthor(principal);

        Post saved = posts.save(Post.notice(category, author, title, content,
                Boolean.TRUE.equals(request.pinned()), now));
        log.info("공지 등록 postId={} adminId={} pinned={}", saved.getId(), principal.userId(),
                saved.isPinned());
        return mapper.toDetail(saved, saved.getViewCount(), true);
    }

    /** 수정 — 요구사항 3.3. */
    @Transactional
    public NoticeDetailResponse update(Long id, NoticeWriteRequest request,
            AuthenticatedUser principal, OffsetDateTime now) {
        guard.requireAdmin(principal);

        Post post = noticeService.findNotice(id);
        String title = requireTitle(request.title());
        String content = requireContent(request.content());
        Category category = requireCategory(request.categoryId(), post.getCategory());

        post.updateContent(title, content, now);
        // 같은 분류를 다시 넣어도 updatedAt 만 바뀌므로 굳이 비교하지 않는다 —
        // 비교 조건을 넣으면 "안 바뀐 것 같은데 왜 바뀌었지" 보다 나쁜 "바꿨는데 안 바뀐" 이 생긴다
        post.changeCategory(category, now);
        post.changePinned(Boolean.TRUE.equals(request.pinned()), now);

        log.info("공지 수정 postId={} adminId={} pinned={}", id, principal.userId(), post.isPinned());
        return mapper.toDetail(post, post.getViewCount(), true);
    }

    /** 삭제 — 요구사항 3.3. 공지사항은 댓글·첨부가 없어 지울 곁가지가 없다(요구사항 0장 차이 표). */
    @Transactional
    public void delete(Long id, AuthenticatedUser principal) {
        guard.requireAdmin(principal);
        Post post = noticeService.findNotice(id);
        posts.delete(post);
        log.info("공지 삭제 postId={} adminId={}", id, principal.userId());
    }

    // ── 검증 — 요구사항 1.2 "검증은 서버에서 한다. 화면 검증은 편의일 뿐이다" ──────────

    /**
     * 제목 — 필수 · 100자 미만.
     *
     * <p>앞뒤 공백을 없애고 나서 센다. 없애지 않으면 공백 100개짜리 제목이 통과하고,
     * 목록에 <b>제목이 없는 줄</b>이 생긴다. (비밀번호와 반대다 — 001 은 비밀번호의 공백을
     * 값의 일부로 봤다. 제목은 표시용 문자열이라 성격이 다르다)
     */
    private String requireTitle(String raw) {
        String title = raw == null ? "" : raw.trim();
        int length = charCount(title);
        if (length == 0) {
            throw new ApiException(ErrorCode.POST_TITLE_REQUIRED);
        }
        if (length > TITLE_MAX_LENGTH) {
            throw new ApiException(ErrorCode.POST_TITLE_LENGTH_INVALID);
        }
        return title;
    }

    /** 내용 — 필수 · 4000자 미만. */
    private String requireContent(String raw) {
        String content = raw == null ? "" : raw.trim();
        int length = charCount(content);
        if (length == 0) {
            throw new ApiException(ErrorCode.POST_CONTENT_REQUIRED);
        }
        if (length > CONTENT_MAX_LENGTH) {
            throw new ApiException(ErrorCode.POST_CONTENT_LENGTH_INVALID);
        }
        return content;
    }

    /**
     * 분류 — 요구사항 3.3 필수.
     *
     * <p>{@code findByIdAndBoardType} 으로 찾는 이유: 자유게시판 분류 id 를 넣어 공지에
     * 붙이는 우회를 막는다. V6 의 {@code ck_posts_category} 는 "분류가 있는가"만 보지
     * "그 게시판의 분류인가"는 못 본다.
     *
     * <p>비활성 분류를 거부하되 <b>현재 글이 이미 쓰고 있는 분류면 통과시킨다.</b>
     * 요구사항 7.2 가 사용 중인 분류를 지우지 않고 비활성으로 내리기 때문에, 비활성을
     * 무조건 막으면 그 분류로 등록된 <b>과거 글을 아무도 수정할 수 없게</b> 된다.
     *
     * @param current 수정 대상 글이 지금 쓰는 분류. 등록이면 {@code null}
     */
    private Category requireCategory(Long categoryId, Category current) {
        if (categoryId == null) {
            throw new ApiException(ErrorCode.CATEGORY_REQUIRED);
        }
        Category category = categories.findByIdAndBoardType(categoryId, NoticeService.BOARD)
                .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));
        boolean keepingCurrent = current != null
                && Objects.equals(current.getId(), category.getId());
        if (!category.isActive() && !keepingCurrent) {
            throw new ApiException(ErrorCode.CATEGORY_NOT_SELECTABLE);
        }
        return category;
    }

    /**
     * 작성자 엔티티.
     *
     * <p>Access 토큰의 {@code userId} 를 그대로 FK 로 쓰지 않고 실제 행을 읽는다 —
     * 토큰은 무상태라(ADR-001) 발급 뒤 탈퇴한 계정의 id 도 그대로 들어 있다.
     * 그 id 로 저장하면 {@code fk_posts_users} 위반이 나 500 이 된다.
     */
    private User requireAuthor(AuthenticatedUser principal) {
        return users.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED));
    }

    /** 길이는 <b>문자 수</b>로 센다 — 001 {@code SignupService} 와 같은 규칙(한글·이모지 허용). */
    private static int charCount(String value) {
        return value.codePointCount(0, value.length());
    }
}
