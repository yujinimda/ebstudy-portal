package com.ebstudy.portal.board.free;

/**
 * 댓글 작성 입력 — 요구사항 4.2.
 *
 * <p>파일이 없으므로 이쪽만 JSON 본문이다(글 등록·수정은 첨부 때문에 multipart 다).
 */
public record FreeCommentWriteRequest(String content) {
}
