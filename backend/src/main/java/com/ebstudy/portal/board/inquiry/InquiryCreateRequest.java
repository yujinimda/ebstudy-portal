package com.ebstudy.portal.board.inquiry;

/**
 * 문의 등록 — 요구사항 6.4.
 *
 * @param secret         비밀글 체크박스. {@code null} 은 꺼진 것으로 본다
 * @param secretPassword 숫자 4자리. {@code secret} 이 꺼져 있으면 <b>무시하고 저장하지 않는다</b>
 *                       (006 Edge Cases — 쓰이지 않는 잠금 값을 남기면 나중에 비밀글로 되돌릴 때
 *                       작성자가 잊은 옛 비밀번호가 되살아난다)
 */
public record InquiryCreateRequest(String title, String content, Boolean secret,
        String secretPassword) {
}
