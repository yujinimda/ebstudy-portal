package com.ebstudy.portal.board.category;

import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.Category;
import com.ebstudy.portal.board.common.CategoryRepository;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분류 조회 — <b>게시판 4종이 공유하는 공개 진입점</b>이다.
 *
 * <p>★ 다른 게시판 패키지(공지·자유·갤러리·문의)는 {@code CategoryRepository} 를 직접 부르지 말고
 * 이 서비스를 부른다. 이유: "분류가 그 게시판 것인가" · "비활성 분류를 새 글에 붙일 수 있는가" ·
 * "문의게시판은 분류가 없다" 세 판정이 게시판마다 복사되면 4벌이 되고,
 * 그중 하나만 빠뜨리면 <b>다른 게시판의 분류 id 를 보내 우회하는 경로</b>가 생긴다.
 * {@code BoardAccessGuard} 를 한 곳에 모은 것과 같은 이유다.
 *
 * <p>읽기 전용이다. 등록·수정·삭제는 {@link CategoryAdminService} 가 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryQueryService {

    private final CategoryRepository categories;

    /**
     * 목록 화면의 분류 드롭다운 — 요구사항 1.1 · 7.2.
     * <b>비활성은 뺀다.</b> 사용자 화면에 "사용 안 함"으로 내린 분류가 보이면 안 된다.
     */
    public List<Category> activeCategories(BoardType boardType) {
        requireCategoryBoard(boardType);
        return categories.findByBoardTypeAndActiveTrueOrderBySortOrderAscIdAsc(boardType);
    }

    /** 위와 같은 목록을 DTO 로. 엔티티를 그대로 응답하지 않는다. */
    public List<CategoryResponse> activeCategoryResponses(BoardType boardType) {
        return activeCategories(boardType).stream().map(CategoryResponse::of).toList();
    }

    /**
     * 글 등록·수정에서 쓰는 진입점 — 게시판 4종이 전부 이것 하나를 부르면 된다.
     *
     * <p>문의게시판이면 {@code null} 을 돌려준다({@code Post.inquiry} 가 분류를 받지 않는다).
     * 그 게시판에 {@code categoryId} 를 보낸 요청은 조용히 무시하지 않고 <b>거부</b>한다 —
     * 무시하면 화면이 잘못 보내는 것을 아무도 모른 채 굴러간다.
     *
     * @param categoryId 요청이 보낸 분류 id. 문의게시판이면 {@code null} 이어야 한다
     * @return 분류가 있는 게시판이면 활성 분류, 문의게시판이면 {@code null}
     */
    public Category resolveForPost(BoardType boardType, Long categoryId) {
        if (!boardType.usesCategory()) {
            if (categoryId != null) {
                throw new ApiException(ErrorCode.CATEGORY_NOT_SUPPORTED);
            }
            return null;
        }
        if (categoryId == null) {
            // 요구사항 4.3 "분류(필수)". V6 ck_posts_category 도 같은 것을 막지만 여기서 먼저 막는다
            throw new ApiException(ErrorCode.CATEGORY_REQUIRED);
        }
        return requireSelectable(boardType, categoryId);
    }

    /**
     * 새로 붙일 수 있는 분류인가 — <b>활성만</b> 통과한다.
     *
     * <p>드롭다운에 없는 값을 직접 보내는 요청을 막는 자리다. 화면에서 감춘 것은 검증이 아니다.
     */
    public Category requireSelectable(BoardType boardType, Long categoryId) {
        Category category = requireAny(boardType, categoryId);
        if (!category.isActive()) {
            throw new ApiException(ErrorCode.CATEGORY_NOT_SELECTABLE);
        }
        return category;
    }

    /**
     * 비활성까지 포함해 찾는다 — <b>이미 저장된 글의 분류를 읽을 때</b> 쓴다.
     *
     * <p>{@link #requireSelectable} 과 나눈 이유: 분류를 비활성으로 내려도 그 분류를 쓰던
     * 과거 글은 그대로 남는다(요구사항 7.2 가 삭제 대신 비활성을 택한 이유가 그것이다).
     * 상세 조회까지 활성만 허용하면 그 글들이 열리지 않는다.
     */
    public Category requireAny(BoardType boardType, Long categoryId) {
        if (categoryId == null) {
            throw new ApiException(ErrorCode.CATEGORY_REQUIRED);
        }
        return find(boardType, categoryId).orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    /** 다른 게시판의 분류 id 로 우회하는 것을 막으려고 {@code boardType} 을 함께 건다. */
    public Optional<Category> find(BoardType boardType, Long categoryId) {
        requireCategoryBoard(boardType);
        if (categoryId == null) {
            return Optional.empty();
        }
        return categories.findByIdAndBoardType(categoryId, boardType);
    }

    /** 요구사항 0장 표 — 문의게시판에는 분류가 없다. V5 {@code ck_categories_board_type} 의 짝. */
    public void requireCategoryBoard(BoardType boardType) {
        if (boardType == null || !boardType.usesCategory()) {
            throw new ApiException(ErrorCode.CATEGORY_NOT_SUPPORTED);
        }
    }
}
