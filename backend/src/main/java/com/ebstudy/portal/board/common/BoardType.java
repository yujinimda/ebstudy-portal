package com.ebstudy.portal.board.common;

import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.util.Locale;

/**
 * 게시판 4종 — 002 요구사항 0장의 차이 표를 <b>코드 한 곳</b>에 옮긴 것이다.
 *
 * <p>왜 여기 모으는가: 이 표가 여기 없으면 "공지는 관리자만" · "문의는 분류가 없다" 같은
 * 판정이 컨트롤러·서비스·화면 세 군데로 흩어지고, 흩어지면 반드시 갈라진다.
 * DB 쪽 짝은 {@code V6__create_posts.sql} 의 CHECK 제약들이다 — 같은 규칙을 DB 도 막는다.
 */
public enum BoardType {

    /** 공지사항(003) — 관리자만 쓴다. 상단 고정이 있다. */
    NOTICE(true, true, false, false, true, false, false),
    /** 자유게시판(002) — 댓글과 첨부파일이 있다. */
    FREE(false, true, true, true, false, false, true),
    /** 갤러리(005) — 이미지 첨부. 첫 이미지가 썸네일이다. */
    GALLERY(false, true, false, true, false, false, true),
    /** 문의게시판(006) — 분류가 없다. 비밀글과 관리자 답변이 있다. */
    INQUIRY(false, false, false, false, false, true, true);

    private final boolean adminOnlyWrite;
    private final boolean usesCategory;
    private final boolean supportsComments;
    private final boolean supportsAttachments;
    private final boolean supportsPinned;
    private final boolean supportsSecret;
    private final boolean keywordIncludesAuthor;

    BoardType(boolean adminOnlyWrite, boolean usesCategory, boolean supportsComments,
            boolean supportsAttachments, boolean supportsPinned, boolean supportsSecret,
            boolean keywordIncludesAuthor) {
        this.adminOnlyWrite = adminOnlyWrite;
        this.usesCategory = usesCategory;
        this.supportsComments = supportsComments;
        this.supportsAttachments = supportsAttachments;
        this.supportsPinned = supportsPinned;
        this.supportsSecret = supportsSecret;
        this.keywordIncludesAuthor = keywordIncludesAuthor;
    }

    /** 요구사항 0장 "글 등록 주체" — 공지사항만 관리자 전용이다. */
    public boolean adminOnlyWrite() {
        return adminOnlyWrite;
    }

    /** 요구사항 0장 "분류" — 문의게시판만 없다. */
    public boolean usesCategory() {
        return usesCategory;
    }

    public boolean supportsComments() {
        return supportsComments;
    }

    public boolean supportsAttachments() {
        return supportsAttachments;
    }

    public boolean supportsPinned() {
        return supportsPinned;
    }

    public boolean supportsSecret() {
        return supportsSecret;
    }

    /** 요구사항 0장 "목록 검색어 범위" — 공지사항만 등록자를 빼고 제목·내용만 본다. */
    public boolean keywordIncludesAuthor() {
        return keywordIncludesAuthor;
    }

    /**
     * 경로 변수·쿼리 파라미터의 문자열을 게시판으로 바꾼다.
     *
     * <p>{@code valueOf} 를 그대로 쓰지 않는 이유: 알 수 없는 값이면
     * {@code IllegalArgumentException} 이 되어 5xx 로 나간다({@code AC-28} 위반).
     * 잘못된 입력은 4xx 여야 한다.
     */
    public static BoardType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
    }
}
