package com.ebstudy.portal.board.gallery;

import com.ebstudy.portal.board.common.Category;

/**
 * 목록 화면의 분류 드롭다운 — 요구사항 1.1
 * <i>"관리자가 등록한 해당 게시판 카테고리 목록"</i>.
 *
 * <p><b>활성 분류만</b> 내려간다(요구사항 7.2). 비활성 분류는 과거 글에는 남아 있지만
 * 새 글에 고를 수는 없어야 하고, 드롭다운에 남겨 두면 고를 수 있게 된다.
 * 비활성까지 보는 것은 관리자 분류 관리 화면(004)의 일이라 여기서 내보내지 않는다.
 */
public record GalleryCategoryResponse(Long id, String name, int sortOrder) {

    public static GalleryCategoryResponse of(Category category) {
        return new GalleryCategoryResponse(category.getId(), category.getName(),
                category.getSortOrder());
    }
}
