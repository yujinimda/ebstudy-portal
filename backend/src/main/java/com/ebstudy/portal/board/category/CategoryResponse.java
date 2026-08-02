package com.ebstudy.portal.board.category;

import com.ebstudy.portal.board.common.Category;

/**
 * 사용자 화면이 보는 분류 — 목록 화면의 분류 드롭다운(요구사항 1.1 "분류").
 *
 * <p>{@code active} 를 담지 않는다: 이 응답에는 <b>활성 분류만</b> 실린다.
 * 항상 {@code true} 인 필드는 화면에 "언제 false 일까"라는 없는 분기를 만든다.
 * {@code createdAt}·{@code updatedAt} 도 뺐다 — 드롭다운이 쓰지 않는 값이다.
 */
public record CategoryResponse(Long id, String boardType, String name, int sortOrder) {

    public static CategoryResponse of(Category category) {
        return new CategoryResponse(category.getId(), category.getBoardType().name(),
                category.getName(), category.getSortOrder());
    }
}
