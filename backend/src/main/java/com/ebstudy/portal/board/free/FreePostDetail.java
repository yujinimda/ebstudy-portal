package com.ebstudy.portal.board.free;

import com.ebstudy.portal.board.common.Category;
import com.ebstudy.portal.board.common.Post;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 상세 — 요구사항 4.2.
 *
 * @param viewCount   ★ <b>이번 조회를 포함한 값</b>이다. 증가시킨 뒤 다시 읽지 않고
 *                    {@code +1} 해서 담는다({@code FreeBoardService.detail} 주석)
 * @param editable    수정 버튼 노출용 — <b>화면 편의값</b>이다. 실제 판정은 요청 때 서버가 다시 한다
 * @param deletable   삭제 버튼 노출용 — 같다
 * @param commentable 요구사항 4.2 "입력은 로그인한 사용자만 보인다"
 */
public record FreePostDetail(
        Long id,
        Long categoryId,
        String categoryName,
        String title,
        String content,
        long viewCount,
        Long authorId,
        String authorName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<FreeAttachmentItem> attachments,
        List<FreeCommentItem> comments,
        boolean editable,
        boolean deletable,
        boolean commentable) {

    static FreePostDetail of(Post post, long viewCount, List<FreeAttachmentItem> attachments,
            List<FreeCommentItem> comments, boolean editable, boolean deletable,
            boolean commentable) {
        Category category = post.getCategory();
        return new FreePostDetail(
                post.getId(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                post.getTitle(),
                post.getContent(),
                viewCount,
                post.getAuthor() == null ? null : post.getAuthor().getId(),
                post.getAuthor() == null ? null : post.getAuthor().getName(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                attachments,
                comments,
                editable,
                deletable,
                commentable);
    }
}
