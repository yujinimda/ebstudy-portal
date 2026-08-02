package com.ebstudy.portal.board.notice;

import com.ebstudy.portal.board.common.PageResponse;

/**
 * 관리자 공지사항 목록 — 요구사항 3.3.
 *
 * <p>사용자 목록({@link NoticeListResponse})과 <b>모양이 다른 것이 핵심이다.</b>
 * 관리 화면에서는 고정 글을 따로 빼지 않고 <b>일반 목록에 섞어서</b> 보여준다.
 *
 * <p>왜: 사용자에게는 최신 5개만 보이지만(요구사항 3.1) 6번째로 고정된 글도
 * <b>DB 에는 고정 상태로 남아 있다</b>. 관리 화면까지 5개만 보여주면 관리자는
 * 그 6번째 글을 <b>찾을 수도 없고 고정을 풀 수도 없다</b>. 관리 화면은 실제 상태를
 * 그대로 보여주고, 각 줄의 {@code pinned} 로 구분한다.
 *
 * @param pinnedCount 지금 고정된 글의 <b>전체</b> 수. {@code pinnedLimit} 을 넘었으면
 *                    화면이 "이 중 최신 5개만 노출됩니다" 를 경고할 수 있다.
 *                    서버는 고정 개수를 <b>막지 않는다</b> — 요구사항 3.1 이
 *                    "넘게 등록하면 최신 5개만 노출" 이라고 했지 "등록을 막는다"고 하지 않았다
 */
public record NoticeAdminListResponse(PageResponse<NoticeListItem> page, long pinnedCount,
        int pinnedLimit) {
}
