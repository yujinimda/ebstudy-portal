package com.ebstudy.portal.board.notice;

/**
 * 공지사항 등록 · 수정 요청 — 요구사항 3.3
 * (분류 · 제목 · 내용 · 상단 고정 체크박스).
 *
 * <p>등록과 수정이 같은 record 인 이유: 보내는 값이 완전히 같다. 두 개로 나누면
 * 한쪽에만 필드가 추가되어 "등록에서는 되는데 수정에서는 안 되는" 차이가 생긴다.
 *
 * <p>모든 필드가 {@code null} 일 수 있는 타입인 것도 의도다 — 화면이 보내지 않은 값을
 * 기본값으로 <b>조용히</b> 채우면 검증이 통과해 버린다. 판정은 {@code NoticeAdminService} 가
 * 한다(요구사항 1.2 "검증은 서버에서 한다").
 *
 * @param pinned {@code null} 은 "체크 안 함"으로 본다. 이 하나만은 조용히 채워도 안전하다 —
 *               고정하지 않는 쪽이 기본이고, 값이 없다고 등록을 거부하면 체크박스를 아예
 *               보내지 않는 클라이언트가 글을 쓸 수 없다
 */
public record NoticeWriteRequest(Long categoryId, String title, String content, Boolean pinned) {
}
