package com.ebstudy.portal.board.gallery;

import com.ebstudy.portal.board.common.Attachment;
import org.springframework.core.io.Resource;

/**
 * 이미지 바이너리 응답에 필요한 것 묶음 — 서비스가 컨트롤러에 넘기는 값이다.
 *
 * <p>{@code Attachment} 엔티티를 컨트롤러까지 올리지 않기 위해 존재한다
 * (엔티티를 그대로 내보내지 않는다는 규약의 연장이다 — {@code storedPath} 가 딸려 올라간다).
 *
 * @param eTag         <b>따옴표까지 포함한</b> 값이다. {@code ETag} 헤더 문법이 그렇고,
 *                     조건부 요청 비교도 따옴표를 포함한 문자열로 한다
 * @param lastModified epoch milli. 갤러리 이미지는 저장 뒤 <b>내용이 바뀌지 않으므로</b>
 *                     첨부 행의 생성 시각이 곧 파일의 최종 변경 시각이다
 */
public record GalleryImageFile(
        String originalName,
        String contentType,
        long sizeBytes,
        String eTag,
        long lastModified,
        Resource resource) {

    public static GalleryImageFile of(Attachment attachment, Resource resource) {
        // 저장 파일은 UUID 이름으로 한 번 쓰고 다시 쓰지 않는다(LocalAttachmentStorage) →
        // id 와 크기만으로 내용이 유일하게 정해진다. 파일을 열어 해시할 이유가 없다
        String eTag = "\"%d-%d\"".formatted(attachment.getId(), attachment.getSizeBytes());
        return new GalleryImageFile(attachment.getOriginalName(), attachment.getContentType(),
                attachment.getSizeBytes(), eTag,
                attachment.getCreatedAt().toInstant().toEpochMilli(), resource);
    }
}
