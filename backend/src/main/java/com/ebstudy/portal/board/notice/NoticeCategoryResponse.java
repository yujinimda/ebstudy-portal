package com.ebstudy.portal.board.notice;

import com.ebstudy.portal.board.common.Category;

/**
 * 공지사항 분류 — 요구사항 1.1 목록의 분류 드롭다운, 3.3 등록/수정 폼의 분류 선택.
 *
 * <p>분류를 <b>만들고 고치는</b> 것은 004 관리자 화면 담당이다. 여기서는 읽기만 한다 —
 * 목록 화면이 쓸 값을 받자고 다른 담당의 API 에 의존하면 배포 순서가 묶인다.
 *
 * @param active 사용자 화면에는 활성만 내려간다. 관리 화면에는 비활성도 함께 내려가는데
 *               <b>이미 그 분류로 등록된 과거 글을 수정</b>할 때 선택지가 사라지면 안 되기
 *               때문이다(요구사항 7.2 — 사용 중인 분류는 지우지 않고 비활성으로 내린다)
 */
public record NoticeCategoryResponse(Long id, String name, int sortOrder, boolean active) {

    public static NoticeCategoryResponse of(Category category) {
        return new NoticeCategoryResponse(category.getId(), category.getName(),
                category.getSortOrder(), category.isActive());
    }
}
