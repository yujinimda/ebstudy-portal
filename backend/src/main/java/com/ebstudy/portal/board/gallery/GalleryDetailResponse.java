package com.ebstudy.portal.board.gallery;

import com.ebstudy.portal.board.common.Attachment;
import com.ebstudy.portal.board.common.Post;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 상세 — 요구사항 5.2 <i>"분류 · 제목 · 등록일시 · 등록자 · 조회수 · 내용 + 이미지 캐러셀"</i>.
 *
 * <p>{@link #images} 는 <b>이미 순서대로</b> 담긴다(0번이 썸네일). 화면이 다시 정렬하면
 * 목록의 썸네일과 캐러셀 첫 장이 어긋날 수 있다 — 순서의 진실은 서버 한 곳이다.
 *
 * @param viewCount 이 응답에는 <b>이번 조회가 반영된</b> 값이 담긴다(요구사항 1.4).
 *                  화면이 새로고침 없이도 올라간 수를 보여줄 수 있게 하기 위해서다
 * @param owned     본인 글인가. 요구사항 1.3 — 화면의 수정·삭제 버튼 노출용 <b>편의값</b>이고
 *                  실제 권한 판정은 서버가 매 요청마다 다시 한다. 이 값을 믿고 검증을 빼지 않는다
 */
public record GalleryDetailResponse(
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
        boolean isNew,
        boolean owned,
        List<GalleryImageResponse> images) {

    public static GalleryDetailResponse of(Post post, List<Attachment> images, long viewCount,
            boolean isNew, boolean owned) {
        return new GalleryDetailResponse(
                post.getId(),
                post.getCategory() == null ? null : post.getCategory().getId(),
                post.getCategory() == null ? null : post.getCategory().getName(),
                post.getTitle(),
                post.getContent(),
                viewCount,
                post.getAuthor().getId(),
                post.getAuthor().getName(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                isNew,
                owned,
                images.stream().map(image -> GalleryImageResponse.of(post.getId(), image)).toList());
    }
}
