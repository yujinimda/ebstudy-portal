package com.ebstudy.portal.board.free;

import com.ebstudy.portal.board.common.Category;

/**
 * 목록 화면의 분류 드롭다운 — 요구사항 1.1
 * <i>"관리자가 등록한 해당 게시판 카테고리 목록"</i>.
 *
 * <p>{@code active} 를 담지 않는다 — 사용자 화면에는 <b>사용 중인 분류만</b> 내려간다
 * (요구사항 7.2). 비활성까지 보는 것은 관리자 카테고리 관리 화면(004)의 일이다.
 * "전체 분류" 항목은 값이 없는 선택지라 서버가 만들지 않고 화면이 붙인다.
 */
public record FreeCategoryItem(Long id, String name, int sortOrder) {

    static FreeCategoryItem of(Category category) {
        return new FreeCategoryItem(category.getId(), category.getName(), category.getSortOrder());
    }
}
