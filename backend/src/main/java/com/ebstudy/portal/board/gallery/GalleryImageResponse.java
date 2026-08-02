package com.ebstudy.portal.board.gallery;

import com.ebstudy.portal.board.common.Attachment;

/**
 * 갤러리 이미지 한 장 — 요구사항 5.2 캐러셀 · 5.1 썸네일.
 *
 * <p>{@code storedPath} 를 내보내지 않는 것이 핵심이다. 그 값은 서버 디스크 구조이고,
 * 응답에 실리는 순간 저장 위치가 밖으로 새며 저장소를 옮길 수도 없게 된다.
 * 화면은 {@link #url} 만 쓴다.
 *
 * @param sortOrder 0번이 썸네일이다(요구사항 5.3). 화면이 순서를 다시 정렬하지 않도록 값을 함께 준다
 */
public record GalleryImageResponse(
        Long id,
        String originalName,
        String url,
        String contentType,
        long sizeBytes,
        int sortOrder) {

    /** 이미지 바이너리 경로 — {@code GalleryController} 의 매핑과 짝이다. */
    public static String urlOf(Long postId, Long attachmentId) {
        return "/api/galleries/%d/images/%d".formatted(postId, attachmentId);
    }

    public static GalleryImageResponse of(Long postId, Attachment attachment) {
        return new GalleryImageResponse(attachment.getId(), attachment.getOriginalName(),
                urlOf(postId, attachment.getId()), attachment.getContentType(),
                attachment.getSizeBytes(), attachment.getSortOrder());
    }
}
