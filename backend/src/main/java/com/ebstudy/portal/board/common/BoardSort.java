package com.ebstudy.portal.board.common;

import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.util.List;
import java.util.Locale;

/**
 * ★ 정렬 기준 화이트리스트 — 요구사항 1.1 "정렬 기준: 기본 등록일시 · 분류 · 제목 · 조회수".
 *
 * <p><b>이 enum 이 존재하는 이유는 SQL 인젝션 방지다.</b> 클라이언트가 보낸 문자열을
 * {@code ORDER BY} 에 그대로 이어 붙이면 {@code sort=title;DROP TABLE posts--} 같은 값이
 * 그대로 들어간다. 문자열은 여기서 <b>enum 상수로 바뀌고</b>, 실제 정렬에 쓰이는 것은
 * 아래에 <b>소스에 적힌 엔티티 속성 이름</b>뿐이다 — 사용자 입력이 쿼리에 닿지 않는다.
 *
 * <p>속성 이름이 컬럼명이 아니라 <b>엔티티 속성 경로</b>인 것도 의도다. JPA 메타모델을
 * 거치므로 없는 속성이면 부팅·쿼리 시점에 실패하지, 임의 SQL 이 되지 않는다.
 */
public enum BoardSort {

    /** 기본값. */
    CREATED_AT(List.of("createdAt")),
    /** 요구사항 1.1 "분류" — 표시 순서(관리자가 정한 순서)로 본다. 이름순이 아니다. */
    CATEGORY(List.of("category.sortOrder", "category.name")),
    TITLE(List.of("title")),
    VIEW_COUNT(List.of("viewCount"));

    private final List<String> properties;

    BoardSort(List<String> properties) {
        this.properties = properties;
    }

    /** Spring Data {@code Sort} 에 넘길 엔티티 속성 경로. 외부 입력이 섞이지 않는다. */
    public List<String> properties() {
        return properties;
    }

    public static BoardSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return CREATED_AT;
        }
        // camelCase(createdAt) · kebab(created-at) · snake(created_at) 를 모두 받는다 —
        // 화면이 어떤 표기를 보내든 같은 상수로 모이게 한다
        String normalized = raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        for (BoardSort candidate : values()) {
            if (candidate.name().equals(normalized)
                    || candidate.name().replace("_", "").equals(normalized)) {
                return candidate;
            }
        }
        // 목록에 없는 값은 조용히 기본값으로 바꾸지 않는다 — 화면이 잘못 보내는 것을 숨기게 된다
        throw new ApiException(ErrorCode.REQUEST_INVALID);
    }
}
