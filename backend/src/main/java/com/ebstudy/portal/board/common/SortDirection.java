package com.ebstudy.portal.board.common;

import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.util.Locale;
import org.springframework.data.domain.Sort;

/**
 * 정렬 방향 — 요구사항 1.1 "기본 내림차순 · 오름차순".
 *
 * <p>{@link BoardSort} 와 같은 이유로 화이트리스트다. 방향도 문자열로 이어 붙이면
 * {@code ORDER BY created_at DESC, (서브쿼리)} 가 만들어질 수 있다.
 */
public enum SortDirection {

    ASC(Sort.Direction.ASC),
    DESC(Sort.Direction.DESC);

    private final Sort.Direction direction;

    SortDirection(Sort.Direction direction) {
        this.direction = direction;
    }

    public Sort.Direction toSpring() {
        return direction;
    }

    public static SortDirection from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DESC;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("ASC") || normalized.equals("ASCENDING")) {
            return ASC;
        }
        if (normalized.equals("DESC") || normalized.equals("DESCENDING")) {
            return DESC;
        }
        throw new ApiException(ErrorCode.REQUEST_INVALID);
    }
}
