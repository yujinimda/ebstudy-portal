package com.ebstudy.portal.board.category;

import com.ebstudy.portal.board.common.Category;
import java.time.OffsetDateTime;

/**
 * 관리 화면이 보는 분류 — 요구사항 7.2 "이름 · 표시 순서 · 사용 여부".
 *
 * @param postCount 이 분류를 쓰는 글 수
 * @param deletable {@code postCount == 0}. 서버가 계산해서 내려 준다 —
 *                  화면이 {@code postCount > 0} 를 스스로 판정하게 두면 "삭제 가능"의 정의가
 *                  화면과 서버 두 곳에 생기고, 나중에 조건이 바뀌면 갈라진다.
 *                  <b>이 값이 false 인데 삭제를 부르면 서버가 다시 막는다</b>
 *                  (요구사항 1.3 — 화면 표시는 검증이 아니다).
 */
public record CategoryAdminResponse(Long id, String boardType, String name, int sortOrder,
        boolean active, long postCount, boolean deletable, OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static CategoryAdminResponse of(Category category, long postCount) {
        return new CategoryAdminResponse(category.getId(), category.getBoardType().name(),
                category.getName(), category.getSortOrder(), category.isActive(),
                postCount, postCount == 0L, category.getCreatedAt(), category.getUpdatedAt());
    }
}
