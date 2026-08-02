package com.ebstudy.portal.board.common;

import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.util.Map;
import java.util.Set;

/**
 * 첨부 정책 — 요구사항 4.3(자유게시판) · 5.3(갤러리).
 *
 * <p>정책은 데이터가 아니라 <b>규칙</b>이라 테이블이 아니라 여기 있다(V8 헤더).
 * 값은 요구사항 문서에서 그대로 옮겼다:
 * <table>
 *   <caption>게시판별 첨부 정책</caption>
 *   <tr><th>게시판</th><th>확장자</th><th>개당 최대</th><th>최대 개수</th></tr>
 *   <tr><td>자유게시판</td><td>jpg gif png zip</td><td>2MB</td><td>5</td></tr>
 *   <tr><td>갤러리</td><td>jpg gif png</td><td>1MB</td><td>20</td></tr>
 * </table>
 *
 * <p>⚠️ {@code jpeg} 를 넣지 않은 것은 의도다 — 요구사항이 {@code jpg} 만 적었고,
 * 요구사항 문서가 유일한 진실이다. 실제로는 {@code .jpeg} 로 저장하는 카메라·도구가 많아
 * 사용자가 막힐 수 있다. <b>사람 검증 대상</b>으로 보고서에 올렸다.
 */
public record AttachmentPolicy(Set<String> allowedExtensions, long maxBytesPerFile, int maxCount) {

    private static final long MB = 1024L * 1024L;

    private static final AttachmentPolicy FREE =
            new AttachmentPolicy(Set.of("jpg", "gif", "png", "zip"), 2 * MB, 5);
    private static final AttachmentPolicy GALLERY =
            new AttachmentPolicy(Set.of("jpg", "gif", "png"), 1 * MB, 20);

    /**
     * 확장자 → Content-Type.
     *
     * <p>★ 클라이언트가 보낸 {@code Content-Type} 을 믿지 않는다. 위조할 수 있고,
     * 위조된 값을 그대로 응답 헤더에 실으면 브라우저가 그 타입으로 해석한다
     * (요구사항 4.2 가 이미지 첨부도 <b>다운로드로 처리</b>하라고 한 것과 같은 방향의 방어다).
     */
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "gif", "image/gif",
            "png", "image/png",
            "zip", "application/zip");

    public static AttachmentPolicy of(BoardType boardType) {
        return switch (boardType) {
            case FREE -> FREE;
            case GALLERY -> GALLERY;
            // 공지·문의는 첨부가 없다(요구사항 0장). 여기 오는 것 자체가 잘못된 호출이다
            case NOTICE, INQUIRY -> throw new ApiException(ErrorCode.REQUEST_INVALID);
        };
    }

    public boolean allows(String extension) {
        return extension != null && allowedExtensions.contains(extension);
    }

    /** 허용 목록에 없는 확장자는 여기 오기 전에 걸러진다 — 그래도 남으면 저장하지 않는다. */
    public String contentTypeOf(String extension) {
        String contentType = CONTENT_TYPES.get(extension);
        if (contentType == null) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
        return contentType;
    }
}
