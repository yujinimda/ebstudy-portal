package com.ebstudy.portal.board.inquiry;

/** 관리자 답변 등록·수정 — 요구사항 6.5. 내용 필수 · 4000자 미만(FR-032 · AC-46). */
public record InquiryAnswerRequest(String content) {
}
