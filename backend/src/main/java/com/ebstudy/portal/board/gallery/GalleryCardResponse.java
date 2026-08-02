package com.ebstudy.portal.board.gallery;

import com.ebstudy.portal.board.common.Attachment;
import com.ebstudy.portal.board.common.Post;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 사용자 목록의 카드 한 장 — 요구사항 5.1 <i>"카드형 — 썸네일(첫 번째) + 제목 + 내용 일부"</i>.
 *
 * <p>{@code extraImageCount} 는 메인 페이지(요구사항 2장)의 {@code +4} 표기용이다 —
 * <b>첫 이미지를 제외한</b> 개수다. 화면에서 {@code imageCount - 1} 을 계산하게 두지 않는 이유는
 * 이미지가 0장인 글에서 {@code -1} 이 나오기 때문이다. 판정은 서버가 한 번만 한다.
 *
 * @param number       요구사항 1.1 "전체 게시글 수 기준 역순". 행 번호가 아니다
 * @param thumbnailUrl 이미지가 없으면 {@code null} — 화면이 대체 이미지를 쓴다
 */
public record GalleryCardResponse(
        long number,
        Long id,
        Long categoryId,
        String categoryName,
        String title,
        String excerpt,
        String thumbnailUrl,
        int imageCount,
        int extraImageCount,
        long viewCount,
        Long authorId,
        String authorName,
        OffsetDateTime createdAt,
        boolean isNew) {

    public static GalleryCardResponse of(long number, Post post, List<Attachment> images,
            String excerpt, boolean isNew) {
        Attachment thumbnail = images.isEmpty() ? null : images.get(0);
        return new GalleryCardResponse(
                number,
                post.getId(),
                post.getCategory() == null ? null : post.getCategory().getId(),
                post.getCategory() == null ? null : post.getCategory().getName(),
                post.getTitle(),
                excerpt,
                thumbnail == null ? null : GalleryImageResponse.urlOf(post.getId(), thumbnail.getId()),
                images.size(),
                Math.max(images.size() - 1, 0),
                post.getViewCount(),
                post.getAuthor().getId(),
                post.getAuthor().getName(),
                post.getCreatedAt(),
                isNew);
    }
}
