package com.ebstudy.portal.board.free;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 등록·수정 입력 — 요구사항 4.3.
 *
 * <p>등록과 수정이 같은 레코드를 쓴다. 요구사항이 두 화면에 같은 필드를 요구하고,
 * 나누면 검증이 두 벌이 되기 때문이다. {@code removeAttachmentIds} 만 수정에서 쓰인다.
 *
 * @param removeAttachmentIds 요구사항 4.3 <i>"수정 시 기존 파일 개별 삭제"</i>.
 *                            <b>남의 글 첨부 id 를 넣는 우회</b>는 서비스가 소유 관계를 다시 확인해 막는다
 * @param files               새로 올리는 파일. 확장자·크기·개수는 전부 서버가 검증한다
 *                            (요구사항 4.3 — 화면의 {@code accept} 속성은 편의일 뿐이다)
 */
public record FreePostWriteRequest(
        Long categoryId,
        String title,
        String content,
        List<Long> removeAttachmentIds,
        List<MultipartFile> files) {
}
