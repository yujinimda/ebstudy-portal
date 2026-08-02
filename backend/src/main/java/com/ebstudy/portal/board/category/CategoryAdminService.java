package com.ebstudy.portal.board.category;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.BoardAccessGuard;
import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.Category;
import com.ebstudy.portal.board.common.CategoryRepository;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분류 관리 — 요구사항 7.2. 관리자만.
 *
 * <p>⚠️ <b>기획서에 이 화면 그림이 없다.</b> 요구사항 7.2 자체가 "Claude 가 판단한 부분"으로
 * 표시된 절이고, 아래 판단들도 그렇다(사람 검증 대상):
 * <ol>
 *   <li><b>삭제와 비활성을 둘 다 연다.</b> 요구사항은 "이미 사용 중인 분류는 삭제하지 않는다 —
 *       비활성으로 내린다"까지만 정했다. 반대로 <i>한 번도 안 쓰인 분류</i>(오타로 만든 것 등)는
 *       지울 수 있어야 관리 화면이 쓰레기로 차지 않는다 → 글이 0건이면 진짜 삭제, 1건이라도
 *       있으면 거부하고 비활성을 안내한다.</li>
 *   <li><b>서버가 자동으로 비활성으로 바꿔 주지 않는다.</b> 삭제를 눌렀는데 조용히 "사용 안 함"이
 *       되면 관리자는 지운 줄 안다. 거부하고 관리자가 다시 고르게 한다.</li>
 *   <li><b>게시판(boardType)은 등록 후 못 바꾼다.</b> 바꾸면 그 분류를 쓰던 글이 통째로
 *       다른 게시판의 분류를 가리키게 된다({@code Category.boardType} 이 {@code updatable=false}).</li>
 *   <li><b>표시 순서는 중복을 허용한다.</b> 유니크로 막으면 순서 하나 바꾸는 데 전부 다시 매겨야 한다.
 *       같은 값이면 id 순으로 안정 정렬한다(V5 주석과 같은 규칙).</li>
 *   <li><b>목록에 페이징을 두지 않는다.</b> 분류는 게시판당 열 개 남짓이다. 페이징을 붙이면
 *       드롭다운을 채우려고 화면이 여러 번 부르게 된다.</li>
 * </ol>
 *
 * <p>권한은 {@code SecurityConfig} 의 {@code /api/admin/** hasRole(ADMIN)} 이 1차로 막지만
 * 여기서 {@link BoardAccessGuard#requireAdmin} 을 한 번 더 부른다 — 경로 규칙 한 줄이 바뀌면
 * 조용히 열리는 종류의 구멍이라, 판정을 서비스에도 둔다(요구사항 1.3).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CategoryAdminService {

    /** V5 {@code name VARCHAR(50)} 과 같은 값. 여기서 먼저 막지 않으면 DB 오류가 500 이 된다. */
    private static final int NAME_MAX = 50;

    /**
     * 표시 순서 상한. 기획에 없어 정했다 — 상한이 없으면 {@code Integer.MAX_VALUE} 가 들어와
     * 그 뒤로 새 분류를 붙일 자리가 없어진다(하한 0 은 V5 {@code ck_categories_sort_order}).
     */
    private static final int SORT_ORDER_MAX = 9999;

    private final CategoryRepository categories;
    private final CategoryAdminRepository adminQueries;
    private final CategoryUsageRepository usage;
    private final BoardAccessGuard guard;

    /** 관리 목록 — <b>비활성까지 전부</b> 본다. 사용자 화면과 다른 점이 이것이다. */
    public List<CategoryAdminResponse> list(BoardType boardType, AuthenticatedUser principal) {
        guard.requireAdmin(principal);
        requireCategoryBoard(boardType);

        List<Category> found = categories.findByBoardTypeOrderBySortOrderAscIdAsc(boardType);
        Map<Long, Long> postCounts = postCountsOf(found);
        return found.stream()
                .map(category -> CategoryAdminResponse.of(category,
                        postCounts.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CategoryAdminResponse create(BoardType boardType, String rawName, Integer rawSortOrder,
            AuthenticatedUser principal) {
        guard.requireAdmin(principal);
        requireCategoryBoard(boardType);

        String name = normalizeName(rawName);
        // 사전 확인은 관리자에게 정확한 이유를 주기 위한 것이고, 유일성의 진짜 판정은
        // V5 유니크 인덱스가 한다(동시 등록 경합 — SignupService 와 같은 구조).
        if (categories.existsByBoardTypeAndNameIgnoringCase(boardType, name)) {
            throw new ApiException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }
        // 순서를 안 적으면 맨 뒤에 붙인다. 0 으로 두면 새 분류가 전부 맨 앞에 몰린다
        int sortOrder = rawSortOrder == null
                ? nextSortOrder(boardType)
                : validSortOrder(rawSortOrder);

        OffsetDateTime now = OffsetDateTime.now();
        Category saved = saveOrDuplicate(Category.create(boardType, name, sortOrder, now));
        log.info("분류 등록 boardType={} categoryId={} name={}", boardType, saved.getId(), name);
        return CategoryAdminResponse.of(saved, 0L);
    }

    /**
     * 이름 · 표시 순서 · 사용 여부를 한 번에 바꾼다(요구사항 7.2 의 필드 셋 그대로).
     *
     * <p>{@code null} 인 항목은 <b>바꾸지 않는다</b> — "사용 안 함"만 토글하는 화면이
     * 이름을 함께 보내지 않아도 되게 한다.
     */
    @Transactional
    public CategoryAdminResponse update(Long categoryId, String rawName, Integer rawSortOrder,
            Boolean active, AuthenticatedUser principal) {
        guard.requireAdmin(principal);

        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));
        OffsetDateTime now = OffsetDateTime.now();

        if (rawName != null) {
            String name = normalizeName(rawName);
            if (adminQueries.existsOtherWithName(category.getBoardType(), name, category.getId())) {
                throw new ApiException(ErrorCode.CATEGORY_NAME_DUPLICATED);
            }
            category.rename(name, now);
        }
        if (rawSortOrder != null) {
            category.reorder(validSortOrder(rawSortOrder), now);
        }
        if (active != null) {
            // 요구사항 7.2 "사용 여부". 비활성으로 내려도 과거 글은 그대로 남는다 —
            // 그것이 삭제 대신 이 플래그를 쓰는 이유다
            if (active) {
                category.activate(now);
            } else {
                category.deactivate(now);
            }
        }

        Category saved = saveOrDuplicate(category);
        log.info("분류 수정 categoryId={} name={} sortOrder={} active={}",
                saved.getId(), saved.getName(), saved.getSortOrder(), saved.isActive());
        return CategoryAdminResponse.of(saved, postCountOf(saved.getId()));
    }

    /**
     * 요구사항 7.2 — <b>이미 글이 사용 중인 분류는 삭제하지 않는다.</b> 비활성으로 내리게 안내한다.
     *
     * <p>미리 세어 보고도 {@link DataIntegrityViolationException} 을 잡는 이유: 세는 순간과
     * 지우는 순간 사이에 누군가 그 분류로 글을 쓸 수 있다. 그때 마지막으로 막는 것은
     * V6 의 {@code fk_posts_categories ON DELETE RESTRICT} 이고, 그 오류를 여기서
     * 잡지 않으면 500 으로 나간다({@code AC-28} — 제약조건명이 밖으로 새는 경로이기도 하다).
     */
    @Transactional
    public void delete(Long categoryId, AuthenticatedUser principal) {
        guard.requireAdmin(principal);

        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));
        if (usage.existsByCategoryId(category.getId())) {
            throw new ApiException(ErrorCode.CATEGORY_IN_USE);
        }
        try {
            categories.delete(category);
            // flush 를 여기서 강제한다 — 안 하면 트랜잭션 커밋 시점에 터져서
            // 이 try 밖으로 빠져나가고 500 이 된다
            categories.flush();
        } catch (DataIntegrityViolationException ex) {
            log.info("사용 중인 분류 삭제 시도 categoryId={}", categoryId);
            throw new ApiException(ErrorCode.CATEGORY_IN_USE);
        }
        log.info("분류 삭제 categoryId={} boardType={}", categoryId, category.getBoardType());
    }

    private Category saveOrDuplicate(Category category) {
        try {
            return categories.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            // 동시에 같은 이름이 들어온 경우. 원본 메시지(인덱스명)를 밖으로 내보내지 않는다(AC-28)
            throw new ApiException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }
    }

    private Map<Long, Long> postCountsOf(List<Category> found) {
        if (found.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        usage.countByCategoryIds(found.stream().map(Category::getId).toList())
                .forEach(row -> counts.put(row.getCategoryId(), row.getTotal()));
        return counts;
    }

    private long postCountOf(Long categoryId) {
        return usage.countByCategoryIds(List.of(categoryId)).stream()
                .findFirst()
                .map(CategoryUsageRepository.CategoryUsage::getTotal)
                .orElse(0L);
    }

    private void requireCategoryBoard(BoardType boardType) {
        // 요구사항 0장 표 — 문의게시판은 분류가 없다. V5 ck_categories_board_type 의 짝이라
        // 여기서 막지 않으면 INSERT 가 CHECK 위반으로 500 이 된다
        if (boardType == null || !boardType.usesCategory()) {
            throw new ApiException(ErrorCode.CATEGORY_NOT_SUPPORTED);
        }
    }

    private String normalizeName(String rawName) {
        // 앞뒤 공백은 지운다 — 비밀번호와 달리 분류명의 공백은 의미가 없고,
        // 남겨 두면 "자유"와 "자유 "가 다른 분류가 되어 유니크 인덱스를 우회한다
        String name = rawName == null ? "" : rawName.strip();
        int length = name.codePointCount(0, name.length());
        if (length < 1 || length > NAME_MAX) {
            // 문자 수로 센다 — Postgres VARCHAR(50) 도 문자 수 기준이다(String.length() 와 다르다)
            throw new ApiException(ErrorCode.CATEGORY_NAME_INVALID);
        }
        return name;
    }

    private int validSortOrder(int sortOrder) {
        if (sortOrder < 0 || sortOrder > SORT_ORDER_MAX) {
            throw new ApiException(ErrorCode.CATEGORY_SORT_ORDER_INVALID);
        }
        return sortOrder;
    }

    private int nextSortOrder(BoardType boardType) {
        return Math.min(adminQueries.maxSortOrder(boardType) + 1, SORT_ORDER_MAX);
    }
}
