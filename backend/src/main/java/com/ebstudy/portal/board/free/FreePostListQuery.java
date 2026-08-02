package com.ebstudy.portal.board.free;

import java.time.LocalDate;

/**
 * 목록 요청 파라미터 — 요구사항 1.1.
 *
 * <p>컨트롤러가 받은 <b>원시 값</b>을 그대로 담는다. 검증·기본값은 여기서 하지 않고
 * {@code BoardSearchCriteria.of(...)} 한 곳에서만 한다 — 게시판마다 검증하면 4벌이 되고
 * 4벌은 반드시 갈라진다.
 *
 * <p>날짜를 {@code LocalDate} 로 받는 이유: 화면의 기간 검색은 <b>날짜 선택기</b>다.
 * 시각까지 받으면 "8월 1일까지" 가 8월 1일 00:00 이 되어 <b>그날 쓴 글이 전부 빠진다</b>.
 * 하루의 시작·끝으로 넓히는 변환은 서비스가 한다({@code FreeBoardService.startOfDay/endOfDay}).
 */
public record FreePostListQuery(
        LocalDate from,
        LocalDate to,
        Long categoryId,
        String keyword,
        Integer page,
        Integer size,
        String sort,
        String direction) {
}
