package com.ebstudy.portal.board.inquiry;

/**
 * 비밀글 잠금해제 — AC-29.
 *
 * <p>★ 본문(POST)으로 받는다. 질의 문자열로 받으면 비밀번호가 <b>접근 로그·리퍼러·브라우저
 * 기록</b>에 그대로 남는다(AC-40 이 금지하는 것과 같은 종류의 누출이다).
 *
 * <p>★ 앞뒤 공백을 <b>제거하지 않는다</b> — 숫자 4자리라 공백이 섞였으면 형식 위반으로
 * 취급하는 것이 맞다(006 Edge Cases).
 */
public record InquiryUnlockRequest(String password) {
}
