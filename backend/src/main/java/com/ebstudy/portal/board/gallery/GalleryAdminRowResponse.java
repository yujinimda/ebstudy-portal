package com.ebstudy.portal.board.gallery;

import com.ebstudy.portal.board.common.Attachment;
import com.ebstudy.portal.board.common.Post;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 관리자 목록의 행 하나 — 요구사항 5.4
 * <i>"번호 · 분류 · 제목(썸네일 + 파일 개수 {@code +8}) · 조회 · 등록일시 · 등록자"</i>.
 *
 * <p>사용자 카드({@link GalleryCardResponse})와 따로 두는 이유: 관리 목록은 <b>표</b>라서
 * 내용 일부(excerpt)를 쓰지 않는다. 하나로 합치면 관리 화면이 쓰지도 않는 본문 일부를
 * 매 행마다 실어 나르게 되고, 반대로 카드에 필요 없는 필드가 늘면 양쪽이 서로를 붙든다.
 */
public record GalleryAdminRowResponse(
        long number,
        Long id,
        Long categoryId,
        String categoryName,
        String title,
        String thumbnailUrl,
        int imageCount,
        int extraImageCount,
        long viewCount,
        Long authorId,
        String authorName,
        OffsetDateTime createdAt,
        boolean isNew) {

    public static GalleryAdminRowResponse of(long number, Post post, List<Attachment> images,
            boolean isNew) {
        Attachment thumbnail = images.isEmpty() ? null : images.get(0);
        return new GalleryAdminRowResponse(
                number,
                post.getId(),
                post.getCategory() == null ? null : post.getCategory().getId(),
                post.getCategory() == null ? null : post.getCategory().getName(),
                post.getTitle(),
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
