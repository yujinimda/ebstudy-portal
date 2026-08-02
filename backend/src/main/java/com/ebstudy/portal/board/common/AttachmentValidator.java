package com.ebstudy.portal.board.common;

import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨부 검증 — 요구사항 4.3 · 5.3 <i>"서버가 검증한다(확장자·크기·개수)"</i>.
 *
 * <p>화면의 {@code accept} 속성과 파일 크기 안내는 <b>편의일 뿐이다</b>(001 {@code FR-002} 와
 * 같은 원칙). 화면을 거치지 않은 직접 호출도 이 코드로 거부된다.
 *
 * <p>⚠️ 스프링의 {@code spring.servlet.multipart.max-file-size} 보다 <b>이 검증이 먼저
 * 걸려야</b> 사용자에게 제대로 된 메시지가 간다. 그래서 application.yml 의 멀티파트 한도는
 * 게시판 정책(2MB)보다 <b>넉넉하게</b> 잡혀 있다 — 스프링 한도에 먼저 걸리면
 * {@code MaxUploadSizeExceededException} 이 되어 500 으로 나간다.
 */
@Component
public class AttachmentValidator {

    /**
     * @param existingCount 수정 화면에서 <b>이미 올라가 있는</b> 개수. 요구사항 4.3 의
     *                      "최대 5개" 는 새로 올리는 개수가 아니라 <b>합계</b>다 —
     *                      이 인자가 없으면 5개짜리 글에 5개를 더 올릴 수 있다
     */
    public void validate(BoardType boardType, List<MultipartFile> files, int existingCount) {
        if (!boardType.supportsAttachments()) {
            // 공지·문의에는 첨부가 없다(요구사항 0장). 파일이 없으면 조용히 통과시킨다 —
            // 빈 멀티파트 파트를 보내는 브라우저가 있다
            if (files != null && !files.isEmpty()) {
                throw new ApiException(ErrorCode.REQUEST_INVALID);
            }
            return;
        }

        AttachmentPolicy policy = AttachmentPolicy.of(boardType);
        int incoming = files == null ? 0 : files.size();
        if (existingCount < 0 || existingCount + incoming > policy.maxCount()) {
            throw new ApiException(ErrorCode.ATTACHMENT_COUNT_EXCEEDED);
        }
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            validateOne(policy, file);
        }
    }

    /** 한 건 검증 — 저장 직전에 구현체도 이것을 다시 부른다(마지막 방어선). */
    public String validateOne(AttachmentPolicy policy, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.ATTACHMENT_EMPTY);
        }
        if (file.getSize() > policy.maxBytesPerFile()) {
            throw new ApiException(ErrorCode.ATTACHMENT_TOO_LARGE);
        }
        String sanitized = AttachmentFilenames.sanitizeOriginalName(file.getOriginalFilename());
        String extension = AttachmentFilenames.extensionOf(sanitized);
        if (!policy.allows(extension)) {
            throw new ApiException(ErrorCode.ATTACHMENT_EXTENSION_NOT_ALLOWED);
        }
        return extension;
    }
}
