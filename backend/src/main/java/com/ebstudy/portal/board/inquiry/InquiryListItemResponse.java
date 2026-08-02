package com.ebstudy.portal.board.inquiry;

import java.time.OffsetDateTime;

/**
 * 목록 한 행 — 요구사항 6.1 컬럼(번호 · 제목 · 답변완료/미답변 · new · 자물쇠 · 조회 ·
 * 등록일시 · 등록자).
 *
 * <p>★ FR-011 · AC-15 — <b>내용과 답변 내용을 담는 필드가 아예 없다.</b>
 * 화면이 안 그리는 것과 서버가 안 보내는 것은 다르다. 응답에 실려 있으면 개발자 도구로 읽힌다.
 * 필드를 두고 {@code null} 을 채우는 방식이 아니라 <b>타입에 자리를 만들지 않는 것</b>이
 * 이 규칙을 지키는 가장 확실한 방법이다.
 *
 * @param number    요구사항 1.1 "전체 게시글 수 기준 역순". 행 번호가 아니다.
 *                  <b>고정 식별자가 아니므로</b> 상세로 가는 링크에는 {@code id} 를 쓴다
 * @param secret    자물쇠 표시. 제목과 등록자는 비밀글이어도 그대로 보인다(AC-22 · 판단 14)
 * @param answered  답변완료/미답변. 판정은 답변 <b>행의 존재 여부</b>다(V9)
 * @param mine      화면이 "나의 문의내역" 을 강조하는 용도. <b>권한 판정에는 쓰지 않는다</b> —
 *                  권한은 언제나 서버가 다시 본다
 */
public record InquiryListItemResponse(
        long number,
        Long id,
        String title,
        boolean secret,
        boolean answered,
        boolean isNew,
        long viewCount,
        OffsetDateTime createdAt,
        String authorName,
        boolean mine) {
}
