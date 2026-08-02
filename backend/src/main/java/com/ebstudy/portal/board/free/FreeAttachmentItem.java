package com.ebstudy.portal.board.free;

import com.ebstudy.portal.board.common.Attachment;

/**
 * 첨부 한 건 — 요구사항 4.2 · 4.3(수정 화면에서 기존 파일 다운로드·개별 삭제).
 *
 * <p>★ {@code storedPath} 를 담지 않는 것은 의도다. 그 값은 서버 디렉터리 구조이고
 * 응답에 실리는 순간 공격자에게 지도를 주는 셈이다({@code AttachmentStorage} 주석).
 * 화면은 {@code downloadUrl} 만 알면 된다.
 *
 * <p>{@code contentType} 도 내보내지 않는다 — 요구사항 4.2 가 <b>이미지라도 다운로드</b>로
 * 처리하라고 했으므로 화면이 타입으로 분기할 이유가 없다.
 */
public record FreeAttachmentItem(Long id, String originalName, long sizeBytes, int sortOrder,
        String downloadUrl) {

    static FreeAttachmentItem of(Attachment attachment, Long postId) {
        return new FreeAttachmentItem(attachment.getId(), attachment.getOriginalName(),
                attachment.getSizeBytes(), attachment.getSortOrder(),
                "/api/free-posts/%d/attachments/%d".formatted(postId, attachment.getId()));
    }
}
