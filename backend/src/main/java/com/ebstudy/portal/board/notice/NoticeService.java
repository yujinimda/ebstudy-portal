package com.ebstudy.portal.board.notice;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.BoardAccessGuard;
import com.ebstudy.portal.board.common.BoardSearchCriteria;
import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.CategoryRepository;
import com.ebstudy.portal.board.common.PageResponse;
import com.ebstudy.portal.board.common.Post;
import com.ebstudy.portal.board.common.PostRepository;
import com.ebstudy.portal.board.common.PostSpecifications;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지사항 — 사용자 조회. 요구사항 3.1 · 3.2.
 *
 * <p>등록·수정·삭제는 여기 없다. 요구사항 0장이 공지사항의 등록 주체를 <b>관리자만</b>으로
 * 정했고, 쓰기 경로는 {@link NoticeAdminService} 하나로 모았다. 읽기와 쓰기를 한 서비스에
 * 두면 "사용자 API 에서 실수로 쓰기가 열리는" 종류의 사고가 한 줄 실수로 가능해진다.
 *
 * <p>목록·상세 조회는 요구사항 1.3 대로 <b>누구나</b> 할 수 있다 — 로그인 검사를 하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NoticeService {

    static final BoardType BOARD = BoardType.NOTICE;

    /**
     * 요구사항 3.1 "상단 고정은 최대 5개".
     *
     * <p>이 값이 {@code 5} 로 코드에 있는 이유: 짝이 되는 쿼리가
     * {@code findTop5By...} 라 메서드 이름에 숫자가 박혀 있다. 설정으로 빼면 설정과
     * 쿼리가 갈라진다 — 둘을 함께 바꿔야 한다는 것을 이 상수가 말한다.
     */
    public static final int PINNED_LIMIT = 5;

    private final PostRepository posts;
    private final NoticeRepository noticeQueries;
    private final CategoryRepository categories;
    private final NoticeMapper mapper;
    private final BoardAccessGuard guard;

    /**
     * 목록 — 요구사항 3.1.
     *
     * <p><b>고정 글과 일반 글을 따로 읽는다.</b> 왜 그래야 하는지는
     * {@link NoticeListResponse} 에 적었다(페이징·개씩보기·번호가 동시에 깨진다).
     *
     * <p>★ 고정 글에는 <b>검색 조건을 걸지 않는다.</b> 요구사항 3.1 이
     * <i>"모든 페이지의 제일 상단에 노출"</i> 이라고 했고, 알림글은 "찾는 글"이 아니라
     * "반드시 보여야 하는 글"이기 때문이다. 검색어를 넣으면 사라지는 알림글은
     * 알림글이 아니다. (검색 시 숨기는 쪽이 낫다는 판단이 나오면 여기 한 줄만 바꾼다)
     */
    @Transactional(readOnly = true)
    public NoticeListResponse list(BoardSearchCriteria criteria, OffsetDateTime now) {
        // 조건 조립은 PostSpecifications 한 곳에서만 한다(공통 기반 규칙).
        // notPinned() 를 붙이지 않으면 1페이지에 고정 글이 두 번 나온다
        Page<Post> page = posts.findAll(
                PostSpecifications.search(criteria, null).and(PostSpecifications.notPinned()),
                criteria.toPageable());

        List<Post> pinned = noticeQueries.withAssociations(
                // 요구사항 3.1 "최대 5개" — 상한은 여기서 자른다(V11 이후 타입으로 조회한다)
                posts.findPinnedNotices(PageRequest.of(0, PINNED_LIMIT)));
        List<Post> items = noticeQueries.withAssociations(page.getContent());

        return new NoticeListResponse(
                // 고정 글은 번호 체계 밖이다 — 요구사항 3.1 "번호 대신 분류명(알림)"
                mapper.toListItems(pinned, 0L, 0, criteria.size(), false, now),
                PageResponse.of(page, mapper.toListItems(items, page.getTotalElements(),
                        criteria.page(), criteria.size(), true, now)));
    }

    /**
     * 상세 — 요구사항 3.2. 조회수는 여기서 증가한다(요구사항 1.4, 단순 증가).
     *
     * <p>{@code readOnly} 가 아닌 이유가 조회수다. 그래서 <b>순서가 중요하다</b>:
     * 응답을 먼저 만들고 그 다음에 증가시킨다. {@code increaseViewCount} 는
     * {@code clearAutomatically = true} 라 영속성 컨텍스트를 비우는데,
     * 비운 뒤에 엔티티를 만지면 LAZY 연관이 다시 터진다.
     */
    @Transactional
    public NoticeDetailResponse read(Long id, AuthenticatedUser principal) {
        Post post = findNotice(id);
        // 관리자에게만 수정·삭제 버튼을 그리게 한다. 값은 힌트일 뿐이고 검증은 관리 API 가 다시 한다
        NoticeDetailResponse response = mapper.toDetail(post, post.getViewCount() + 1,
                guard.isAdmin(principal));
        posts.increaseViewCount(id);
        return response;
    }

    /**
     * 메인 페이지(요구사항 2장) — 공지사항 최신 5개.
     *
     * <p><b>목록 API 를 재사용하지 않는 이유</b>: 목록은 요구사항 1.1 에 따라 기간이
     * <b>기본 1달</b>이다. 메인은 기간 개념이 없으므로 그대로 쓰면 한 달 넘게 글이 없는
     * 게시판이 <b>빈 칸</b>으로 보인다. 사용자에게는 고장으로 읽힌다.
     *
     * <p>고정 글을 따로 올리지 않는다 — 메인은 5줄뿐이라 고정 글까지 얹으면
     * 최신 글이 밀려난다. 요구사항 2장은 <i>"최신 5개"</i> 만 요구한다.
     */
    @Transactional(readOnly = true)
    public List<NoticeListItem> latest(int limit, OffsetDateTime now) {
        if (limit < 1 || limit > 20) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
        List<Post> content = noticeQueries.withAssociations(
                posts.findByBoardTypeOrderByCreatedAtDescIdDesc(BOARD, PageRequest.of(0, limit)));
        // 메인은 페이지가 없다 → 번호 기준은 게시판 전체 수(목록 1페이지와 같은 번호가 나온다)
        return mapper.toListItems(content, posts.countByBoardType(BOARD), 0, limit, true, now);
    }

    /** 목록 화면의 분류 드롭다운 — 요구사항 1.1. 비활성 분류는 새 검색에 쓸 이유가 없다. */
    @Transactional(readOnly = true)
    public List<NoticeCategoryResponse> activeCategories() {
        return categories.findByBoardTypeAndActiveTrueOrderBySortOrderAscIdAsc(BOARD).stream()
                .map(NoticeCategoryResponse::of)
                .toList();
    }

    /**
     * 공지 한 건을 찾는다. {@code boardType} 을 함께 보는 이유는 다른 게시판 글의 id 로
     * 공지 화면에 들어오는 우회를 막기 위해서다.
     *
     */
    Post findNotice(Long id) {
        return noticeQueries.findDetail(id, BOARD)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }
}
