package com.ebstudy.portal.board.inquiry;

import com.ebstudy.portal.board.common.InquiryAnswer;
import java.time.OffsetDateTime;

/**
 * 관리자 답변 — 요구사항 6.3 · 006 Assumptions 5(관리자 이름과 답변일시를 함께 보여준다).
 *
 * <p>★ FR-035 · AC-41 — 비밀글의 답변은 본문과 <b>동일한 보호</b>를 받는다.
 * 답변은 질문을 인용하기 마련이라 본문보다 더 많이 말한다. 그래서 열람 권한이 없는 요청에는
 * 이 객체를 <b>만들지도 않는다</b>({@code InquiryService} 의 잠금 경로는 답변을 조회조차 하지
 * 않는다 — 메모리에 올리지 않으면 샐 수 없다).
 */
public record InquiryAnswerResponse(
        Long id,
        String content,
        String adminName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static InquiryAnswerResponse of(InquiryAnswer answer) {
        return new InquiryAnswerResponse(answer.getId(), answer.getContent(),
                answer.getAdmin().getName(), answer.getCreatedAt(), answer.getUpdatedAt());
    }
}
