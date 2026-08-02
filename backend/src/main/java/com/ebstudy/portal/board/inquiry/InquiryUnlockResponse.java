package com.ebstudy.portal.board.inquiry;

/**
 * 비밀글 잠금해제 결과 — AC-29 · AC-37.
 *
 * @param grantToken 이 <b>글 하나</b>에 대한 열람 통과. 다음 상세 조회에서
 *                   {@code X-Inquiry-Grant} 헤더로 되돌려 보내면 비밀번호를 다시 묻지 않는다.
 *                   유효 시간이 지나면 만료된다({@link SecretReadGrantService}).
 *                   ★ 이 값으로 얻는 것은 <b>열람뿐</b>이다 — 수정·삭제는 소유자 확인 하나로만
 *                   판정한다(FR-022 · AC-33). 4자리를 맞혀 남의 글을 지울 수 있다면
 *                   1만 번 시도로 게시판을 삭제할 수 있다
 */
public record InquiryUnlockResponse(String grantToken, InquiryDetailResponse inquiry) {
}
