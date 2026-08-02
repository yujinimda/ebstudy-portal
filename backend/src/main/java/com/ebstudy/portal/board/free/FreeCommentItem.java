package com.ebstudy.portal.board.free;

import com.ebstudy.portal.board.common.Comment;
import java.time.OffsetDateTime;

/**
 * 댓글 한 건 — 요구사항 4.2 <i>"작성자 이름 · 작성일시 · 내용"</i>.
 *
 * @param deletable ★ <b>화면 편의값일 뿐이다.</b> 이 값이 {@code true} 라서 지워지는 것이 아니라
 *                  삭제 요청이 들어올 때 서버가 다시 판정한다
 *                  ({@code BoardAccessGuard.requireOwnerOrAdmin} — 요구사항 1.3 · 001 AC-26).
 *                  버튼을 숨기는 것은 권한 검증이 아니다
 */
public record FreeCommentItem(Long id, Long authorId, String authorName, String content,
        OffsetDateTime createdAt, boolean deletable) {

    static FreeCommentItem of(Comment comment, boolean deletable) {
        return new FreeCommentItem(comment.getId(),
                comment.getAuthor() == null ? null : comment.getAuthor().getId(),
                comment.getAuthor() == null ? null : comment.getAuthor().getName(),
                comment.getContent(), comment.getCreatedAt(), deletable);
    }
}
