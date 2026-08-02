package com.ebstudy.portal.board.common;

import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 목록 검색 조건 — 요구사항 1.1. 게시판 4종이 이 하나를 공유한다.
 *
 * <p><b>여기가 유일한 검증 지점이다.</b> 컨트롤러는 문자열을 받아
 * {@link #of} 에 넘기기만 하고, 이 뒤로는 <b>검증을 통과한 값</b>만 흐른다.
 * 게시판마다 따로 검증하면 4벌이 되고 4벌은 반드시 갈라진다(001 {@code FR-002} 와 같은 원칙 —
 * 검증은 서버에서, 한 곳에서).
 *
 * <p>보관하는 값은 전부 정규화·검증이 끝난 것이다:
 * <ul>
 *   <li>기간 — 기본 최근 1달, <b>최대 1년</b>. 넘으면 거부한다</li>
 *   <li>개씩 보기 — 10 · 20 · 30 · 40 · 50 만</li>
 *   <li>정렬 — {@link BoardSort} · {@link SortDirection} 화이트리스트</li>
 * </ul>
 */
public record BoardSearchCriteria(
        BoardType boardType,
        OffsetDateTime from,
        OffsetDateTime to,
        Long categoryId,
        String keyword,
        /** 요구사항 6.1 "나의 문의내역만 보기". 로그인하지 않았으면 {@link #of} 가 꺼 버린다. */
        boolean mineOnly,
        int page,
        int size,
        BoardSort sort,
        SortDirection direction) {

    /** 요구사항 1.1 "개씩 보기: 기본 10 · 20 · 30 · 40 · 50". */
    public static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 30, 40, 50);
    public static final int DEFAULT_SIZE = 10;
    /** 요구사항 1.1 "기본 1달". */
    public static final int DEFAULT_PERIOD_MONTHS = 1;
    /** 요구사항 1.1 "최대 1년까지만". */
    public static final int MAX_PERIOD_YEARS = 1;
    /** 화면이 아무리 큰 페이지 번호를 보내도 여기서 끊는다 — 깊은 페이징으로 DB 를 태우지 않는다. */
    public static final int MAX_PAGE = 10_000;
    /** 검색어가 길면 LIKE 스캔 비용만 커진다. 화면 입력 길이와 무관하게 서버가 자른다. */
    public static final int MAX_KEYWORD_LENGTH = 100;

    /**
     * 화면이 보낸 원시 값에서 검증된 조건을 만든다.
     *
     * @param mineOnlyRequested 요구사항 6.1 체크박스. {@code viewerId} 가 없으면 무시된다 —
     *                          로그인하지 않은 사람의 "내 글" 은 정의되지 않는다
     * @param viewerId          로그인한 사용자 id. 비로그인이면 {@code null}
     * @param now               "지금". 인자로 받는 이유는 테스트가 시간을 고정할 수 있어야 하기 때문이다
     */
    public static BoardSearchCriteria of(BoardType boardType, OffsetDateTime from,
            OffsetDateTime to, Long categoryId, String keyword, Boolean mineOnlyRequested,
            Long viewerId, Integer page, Integer size, String sort, String direction,
            OffsetDateTime now) {

        if (boardType == null || now == null) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }

        // 끝을 먼저 정한다 — 시작만 준 경우와 아무것도 안 준 경우를 같은 규칙으로 처리하려면
        // "끝 기준 1달 전" 이라는 하나의 계산만 있으면 된다
        OffsetDateTime resolvedTo = to != null ? to : now;
        OffsetDateTime resolvedFrom = from != null ? from
                : resolvedTo.minusMonths(DEFAULT_PERIOD_MONTHS);

        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
        // 요구사항 1.1 "최대 1년까지만 검색 가능".
        // 화면에서 날짜 선택기를 막는 것은 편의일 뿐이고, 직접 호출도 같은 코드로 거부된다
        if (resolvedFrom.plusYears(MAX_PERIOD_YEARS).isBefore(resolvedTo)) {
            throw new ApiException(ErrorCode.BOARD_PERIOD_TOO_LONG);
        }

        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        if (!ALLOWED_SIZES.contains(resolvedSize)) {
            throw new ApiException(ErrorCode.BOARD_LIST_OPTION_INVALID);
        }

        int resolvedPage = page == null ? 0 : page;
        if (resolvedPage < 0 || resolvedPage > MAX_PAGE) {
            throw new ApiException(ErrorCode.BOARD_LIST_OPTION_INVALID);
        }

        BoardSort resolvedSort = BoardSort.from(sort);
        // 문의게시판에는 분류가 없다(요구사항 0장). 분류 정렬을 허용하면 category 조인이 만들어져
        // 결과가 조용히 비거나 순서가 무의미해진다 → 조건을 받는 자리에서 거부한다
        if (resolvedSort == BoardSort.CATEGORY && !boardType.usesCategory()) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
        if (categoryId != null && !boardType.usesCategory()) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }

        String resolvedKeyword = normalizeKeyword(keyword);
        boolean resolvedMineOnly = Boolean.TRUE.equals(mineOnlyRequested) && viewerId != null;

        return new BoardSearchCriteria(boardType, resolvedFrom, resolvedTo, categoryId,
                resolvedKeyword, resolvedMineOnly, resolvedPage, resolvedSize, resolvedSort,
                SortDirection.from(direction));
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_KEYWORD_LENGTH
                ? trimmed.substring(0, MAX_KEYWORD_LENGTH)
                : trimmed;
    }

    public boolean hasKeyword() {
        return keyword != null;
    }

    /**
     * {@code LIKE} 패턴 — 요구사항 1.1 "부분 일치".
     *
     * <p>{@code %} 와 {@code _} 를 이스케이프한다. 하지 않으면 사용자가 {@code %} 하나만 넣어
     * <b>전체 행을 훑게</b> 만들 수 있고, {@code _} 는 의도하지 않은 한 글자 매칭이 된다.
     * 이스케이프 문자는 {@code PostSpecifications} 가 {@code LIKE ... ESCAPE '\'} 로 함께 넘긴다.
     */
    public String likePattern() {
        if (keyword == null) {
            return null;
        }
        String escaped = keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped.toLowerCase() + "%";
    }

    /**
     * Spring Data 페이지 요청으로 바꾼다.
     *
     * <p>정렬 마지막에 {@code id} 를 붙이는 것이 핵심이다 — 등록일시가 같은 글이 여럿이면
     * 순서가 매 쿼리마다 달라지고, 그러면 <b>2페이지에 1페이지의 글이 다시 나온다</b>
     * (V6 의 {@code ix_posts_board_type_created_at} 이 {@code id DESC} 를 포함하는 것과 같은 짝).
     */
    public Pageable toPageable() {
        List<Sort.Order> orders = new ArrayList<>();
        for (String property : sort.properties()) {
            orders.add(new Sort.Order(direction.toSpring(), property));
        }
        if (!sort.properties().contains("id")) {
            orders.add(new Sort.Order(direction.toSpring(), "id"));
        }
        return PageRequest.of(page, size, Sort.by(orders));
    }
}
