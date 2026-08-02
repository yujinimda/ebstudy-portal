package com.ebstudy.portal.board.notice;

import com.ebstudy.portal.board.common.PageResponse;
import java.util.List;

/**
 * 공지사항 목록 응답 — 요구사항 3.1.
 *
 * <p><b>고정 글과 일반 글을 한 배열에 섞지 않는 것이 이 응답의 전부다.</b>
 * 섞으면 다음 세 가지가 동시에 깨진다.
 * <ol>
 *   <li>요구사항 3.1 은 고정 글이 <b>모든 페이지</b> 상단에 온다고 했다. 한 배열이면
 *       고정 글이 1페이지의 자리를 먹고 2페이지에는 사라진다</li>
 *   <li>{@code size=10} 인데 고정 5개가 섞이면 한 페이지의 일반 글이 5건이 된다 —
 *       "10개씩 보기"가 거짓이 된다</li>
 *   <li>번호가 어긋난다. 고정 글은 번호 체계 밖인데(위 1번) 같은 배열에 있으면
 *       {@code totalElements} 기준 역순 계산에 끼어든다</li>
 * </ol>
 * 그래서 <b>{@code pinned} 는 페이징 밖</b>이고, {@code page} 안에는 고정 글이 없다
 * ({@code PostSpecifications.notPinned()}). 화면은 매 페이지 {@code pinned} 를 먼저,
 * 그 아래 {@code page.items} 를 그린다.
 *
 * @param pinned 최대 5건. 요구사항 3.1 "5개를 넘게 등록하면 최신 5개만" 을 <b>조회에서</b>
 *               자른 결과다(등록을 막지 않는다 — 요구사항 문구 그대로)
 * @param page   일반 글. {@code totalElements}·{@code totalPages} 는 <b>고정 글을 뺀</b> 수다
 */
public record NoticeListResponse(List<NoticeListItem> pinned, PageResponse<NoticeListItem> page) {
}
