package com.ebstudy.portal.board.inquiry;

import java.time.OffsetDateTime;

/**
 * 문의 상세 — 요구사항 6.3.
 *
 * <p>두 가지 모양으로 쓰인다.
 * <ul>
 *   <li><b>열린 상세</b> — {@code locked=false}, 내용과 답변이 들어 있다</li>
 *   <li><b>잠금 안내</b>({@code GET /{id}/preview}) — {@code locked=true},
 *       {@code content} 와 {@code answer} 가 {@code null} 이고
 *       <b>목록에 이미 공개된 항목만</b> 들어 있다(AC-28 의 근거와 같다:
 *       비밀번호 입력 화면에 "어떤 글의 비밀번호를 넣는지" 를 보여주려는 것이고,
 *       그 항목들은 목록에서 이미 보이므로 새로 새는 정보가 없다)</li>
 * </ul>
 *
 * @param editable 요구사항 6.3 "수정(본인 + 미답변일 때만)". <b>화면 편의값일 뿐이다</b> —
 *                 이 값이 {@code true} 라서 수정이 되는 것이 아니라, 수정 요청이 오면
 *                 서버가 소유자와 답변 여부를 <b>다시</b> 본다(FR-014 · FR-015 · AC-19 · AC-20).
 *                 버튼을 숨기는 것은 권한 검증이 아니다
 * @param answered 답변완료/미답변. {@code locked=true} 여도 <b>유무는</b> 보인다
 *                 (요구사항 6.1 이 목록 컬럼으로 이미 공개한 값이다 — AC-41)
 */
public record InquiryDetailResponse(
        Long id,
        String title,
        String content,
        boolean secret,
        boolean locked,
        boolean answered,
        long viewCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String authorName,
        boolean mine,
        boolean editable,
        boolean deletable,
        InquiryAnswerResponse answer) {
}
