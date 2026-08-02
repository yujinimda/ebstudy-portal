package com.ebstudy.portal.board.common;

import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.util.Locale;

/**
 * 업로드 파일명 정제 — <b>경로 순회 방어의 1차 방어선</b>.
 *
 * <p>브라우저가 보내는 {@code filename} 은 사용자가 통제하는 값이다. 다음이 실제로 들어온다:
 * <ul>
 *   <li>{@code ../../../../etc/passwd} — 상위 디렉터리로 나간다</li>
 *   <li>{@code C:\Users\x\a.png} — 윈도우 클라이언트가 전체 경로를 보낸다</li>
 *   <li>{@code a.png%00.jsp} · 널 바이트 — 확장자 검사를 우회한다</li>
 *   <li>{@code .png} · {@code noext} — 확장자가 없다</li>
 * </ul>
 *
 * <p>그래서 <b>이름에서 확장자만 뽑아 쓰고, 저장 이름은 서버가 새로 만든다.</b>
 * DB 쪽 짝은 V8 의 {@code ck_attachments_stored_path_safe} 다.
 */
public final class AttachmentFilenames {

    /** V8 {@code original_name VARCHAR(255)} 와 같은 값. */
    public static final int MAX_ORIGINAL_NAME_LENGTH = 255;

    private AttachmentFilenames() {
    }

    /**
     * 보여주기용 이름 — 경로 구분자와 제어문자를 지운 <b>마지막 구간</b>만 남긴다.
     *
     * <p>다운로드 응답의 {@code Content-Disposition} 에 그대로 실리므로, 개행이 남아 있으면
     * 헤더 인젝션이 된다. 그래서 제어문자를 지우는 것이 필수다.
     */
    public static String sanitizeOriginalName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
        // 경로 구분자 두 종류를 모두 자른다 — 윈도우 클라이언트는 역슬래시를 보낸다
        String base = rawName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        // 제어문자(널 바이트·개행 포함) 제거. 남기면 헤더 인젝션·확장자 우회 경로가 된다
        base = base.replaceAll("\\p{Cntrl}", "").trim();
        // 앞의 점은 지운다 — ".." 과 숨김 파일이 여기서 걸러진다
        while (base.startsWith(".")) {
            base = base.substring(1);
        }
        if (base.isBlank()) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
        return base.length() > MAX_ORIGINAL_NAME_LENGTH
                ? base.substring(base.length() - MAX_ORIGINAL_NAME_LENGTH)
                : base;
    }

    /**
     * 확장자만 뽑는다(점 없이, 소문자).
     *
     * <p>★ <b>마지막</b> 점 뒤를 본다. {@code a.png.exe} 는 {@code exe} 다 —
     * 첫 점을 보면 {@code exe} 파일을 {@code png} 로 통과시키게 된다.
     */
    public static String extensionOf(String sanitizedName) {
        int dot = sanitizedName.lastIndexOf('.');
        if (dot < 0 || dot == sanitizedName.length() - 1) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
        String extension = sanitizedName.substring(dot + 1).toLowerCase(Locale.ROOT);
        // 확장자에 영숫자 외의 것이 남아 있으면 정제가 실패한 것이다 — 저장하지 않는다
        if (!extension.matches("^[a-z0-9]{1,10}$")) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
        return extension;
    }
}
