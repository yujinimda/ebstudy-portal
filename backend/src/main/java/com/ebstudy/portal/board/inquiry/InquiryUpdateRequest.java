package com.ebstudy.portal.board.inquiry;

/**
 * 문의 수정 — 요구사항 6.4. 본인 + <b>미답변일 때만</b>(FR-015).
 *
 * @param secret         꺼서 보내면 비밀글이 해제되고 <b>저장된 비밀번호도 지워진다</b>
 *                       (FR-016 · AC-26)
 * @param secretPassword 비워서 보내면 <b>기존 비밀번호를 유지</b>한다(006 Edge Cases).
 *                       비운 것을 "삭제" 로 해석하면 AC-23 이 금지한
 *                       <i>비밀번호 없는 비밀글</i> 이 만들어진다.
 *                       단 공개글을 비밀글로 <b>바꾸는</b> 경우에는 유지할 값이 없으므로 필수다
 */
public record InquiryUpdateRequest(String title, String content, Boolean secret,
        String secretPassword) {
}
