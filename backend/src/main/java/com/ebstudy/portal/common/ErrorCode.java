package com.ebstudy.portal.common;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드 — contracts/auth-api.md 의 `code` 열이 단일 진실이다.
 *
 * <p>메시지(detail)를 여기 한 곳에 모은 이유: {@code AC-2}·{@code AC-3}·{@code AC-32} 는
 * 응답의 detail 까지 완전히 같아야 한다. 문구를 호출 지점에서 만들면 세 경로가 갈라진다.
 */
public enum ErrorCode {

    // ── 인증 ────────────────────────────────────────────────
    /** AC-2 · AC-3 · AC-32 — 세 경로가 이 하나를 공유한다. 절대로 세분화하지 않는다. */
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, Type.AUTHENTICATION, "Authentication Failed",
            "아이디 또는 비밀번호를 확인해 주세요"),
    AUTH_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, Type.RATE_LIMIT, "Too Many Requests",
            "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요"),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, Type.AUTHENTICATION, "Authentication Required",
            "로그인이 필요합니다"),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, Type.AUTHORIZATION, "Access Denied",
            "이 기능을 사용할 권한이 없습니다"),
    AUTH_REFRESH_INVALID(HttpStatus.UNAUTHORIZED, Type.AUTHENTICATION, "Authentication Failed",
            "로그인 정보가 유효하지 않습니다. 다시 로그인해 주세요"),
    AUTH_REFRESH_EXPIRED(HttpStatus.UNAUTHORIZED, Type.AUTHENTICATION, "Authentication Failed",
            "로그인 유효 기간이 만료되었습니다. 다시 로그인해 주세요"),

    // ── 회원가입 ─────────────────────────────────────────────
    USER_ID_DUPLICATED(HttpStatus.CONFLICT, Type.CONFLICT, "Conflict",
            "이미 사용 중인 아이디입니다"),
    USER_ID_LENGTH_INVALID(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "아이디는 4자 이상 12자 미만이어야 합니다"),
    USER_ID_FORMAT_INVALID(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "아이디는 영문·숫자와 -, _ 만 사용할 수 있습니다"),
    USER_ID_NOT_ALLOWED(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "사용할 수 없는 아이디입니다"),
    USER_PASSWORD_LENGTH_INVALID(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "비밀번호 길이가 허용 범위를 벗어났습니다"),
    USER_PASSWORD_CONTAINS_ID(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "비밀번호에 아이디를 포함할 수 없습니다"),
    USER_PASSWORD_REPEATED_CHAR(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "같은 문자를 3회 이상 연속으로 사용할 수 없습니다"),
    USER_NAME_LENGTH_INVALID(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "이름 길이가 허용 범위를 벗어났습니다"),

    // ── 중복확인 ─────────────────────────────────────────────
    CHECK_ID_TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, Type.RATE_LIMIT, "Too Many Requests",
            "중복확인 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요"),

    // ── 게시판 공통 ──────────────────────────────────────────
    // 002 통합 — 게시판 4종이 같은 규칙에 같은 코드를 쓴다. 화면이 게시판마다 분기하지
    // 않게 하려는 것이다. 병렬 작업 중에는 common/ 이 잠겨 있어 각 패키지가 임시로
    // REQUEST_INVALID 를 쓰거나 errors[0].field 에 코드 이름을 실어 보냈고, 그 결과
    // "제목이 길다" 하나에 게시판별로 다른 코드가 나갔다. 통합 단계에서 하나로 모은다.
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, Type.NOT_FOUND, "Not Found",
            "요청하신 글을 찾을 수 없습니다"),
    POST_TITLE_REQUIRED(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "제목을 입력해 주세요"),
    POST_TITLE_LENGTH_INVALID(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "제목은 100자 미만이어야 합니다"),
    POST_CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "내용을 입력해 주세요"),
    POST_CONTENT_LENGTH_INVALID(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "내용은 4000자 미만이어야 합니다"),
    /** 요구사항 1.1 — "최대 1년까지만 검색 가능". 화면의 날짜 선택기 제한은 편의일 뿐이다. */
    BOARD_PERIOD_TOO_LONG(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "검색 기간은 최대 1년까지만 조회할 수 있습니다"),
    BOARD_LIST_OPTION_INVALID(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "목록 조회 조건이 올바르지 않습니다"),

    // ── 댓글 ────────────────────────────────────────────────
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, Type.NOT_FOUND, "Not Found",
            "댓글을 찾을 수 없습니다"),
    COMMENT_CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "댓글 내용을 입력해 주세요"),
    COMMENT_CONTENT_LENGTH_INVALID(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "댓글은 1000자 미만이어야 합니다"),

    // ── 첨부 ────────────────────────────────────────────────
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, Type.NOT_FOUND, "Not Found",
            "첨부파일을 찾을 수 없습니다"),
    ATTACHMENT_EMPTY(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "빈 파일은 첨부할 수 없습니다"),
    ATTACHMENT_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "첨부할 수 있는 파일 개수를 넘었습니다"),
    ATTACHMENT_TOO_LARGE(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "첨부파일 용량이 허용 범위를 넘었습니다"),
    ATTACHMENT_EXTENSION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "허용하지 않는 파일 형식입니다"),

    // ── 분류(카테고리) ───────────────────────────────────────
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, Type.NOT_FOUND, "Not Found",
            "분류를 찾을 수 없습니다"),
    CATEGORY_NAME_INVALID(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "분류 이름은 1자 이상 50자 이하여야 합니다"),
    CATEGORY_NAME_DUPLICATED(HttpStatus.CONFLICT, Type.CONFLICT, "Conflict",
            "같은 이름의 분류가 이미 있습니다"),
    CATEGORY_SORT_ORDER_INVALID(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "표시 순서가 올바르지 않습니다"),
    /** 요구사항 7.2 — "이미 사용 중인 분류는 삭제하지 않는다". 비활성으로 내리게 안내한다. */
    CATEGORY_IN_USE(HttpStatus.CONFLICT, Type.CONFLICT, "Conflict",
            "이미 글이 사용 중인 분류는 삭제할 수 없습니다. 사용 안 함으로 바꿔 주세요"),
    CATEGORY_REQUIRED(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "분류를 선택해 주세요"),
    /** 요구사항 0장 표 — 문의게시판에는 분류가 없다. */
    CATEGORY_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "이 게시판은 분류를 사용하지 않습니다"),
    /** 비활성 분류를 새 글에 붙이려는 시도 — 목록 드롭다운에 없는 값을 직접 보낸 경우다. */
    CATEGORY_NOT_SELECTABLE(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "사용할 수 없는 분류입니다"),

    // ── 갤러리 ──────────────────────────────────────────────
    GALLERY_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "이미지를 1장 이상 등록해 주세요"),

    // ── 비밀글 잠금 비밀번호 ──────────────────────────────────
    /**
     * 비밀글 상세에 권한 없이 들어왔다 — <b>403 이다. 401 이 아니다.</b>
     * 401 은 이 프로젝트에서 로그인 자격증명 문제로 이미 쓰이고 화면이 재발급·로그인 이동으로
     * 반응한다(001 FR-016 · FR-020). 401 을 쓰면 글 비밀번호를 한 번 틀렸다고 로그아웃된다.
     */
    SECRET_POST_LOCKED(HttpStatus.FORBIDDEN, Type.AUTHORIZATION, "Access Denied",
            "비밀글입니다. 비밀번호를 입력해 주세요"),
    SECRET_PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "비밀글은 잠금 비밀번호 4자리를 입력해야 합니다"),
    SECRET_PASSWORD_FORMAT_INVALID(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "잠금 비밀번호는 숫자 4자리여야 합니다"),
    SECRET_PASSWORD_MISMATCH(HttpStatus.FORBIDDEN, Type.AUTHORIZATION, "Access Denied",
            "비밀번호가 일치하지 않습니다"),
    SECRET_PASSWORD_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, Type.RATE_LIMIT,
            "Too Many Requests", "비밀번호 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요"),

    // ── 문의게시판 ───────────────────────────────────────────
    /** 비밀글이 아닌 글에 잠금해제를 요청했다. */
    INQUIRY_NOT_SECRET(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "비밀글이 아닙니다"),
    /** 요구사항 6.3 — 수정·삭제는 <b>미답변일 때만</b> 된다. */
    INQUIRY_ALREADY_ANSWERED(HttpStatus.CONFLICT, Type.CONFLICT, "Conflict",
            "답변이 등록된 문의는 수정·삭제할 수 없습니다"),
    INQUIRY_ANSWER_ALREADY_EXISTS(HttpStatus.CONFLICT, Type.CONFLICT, "Conflict",
            "이미 답변이 등록된 문의입니다"),
    INQUIRY_ANSWER_NOT_FOUND(HttpStatus.NOT_FOUND, Type.NOT_FOUND, "Not Found",
            "등록된 답변이 없습니다"),
    INQUIRY_ANSWER_CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "답변 내용을 입력해 주세요"),
    INQUIRY_ANSWER_CONTENT_LENGTH_INVALID(HttpStatus.BAD_REQUEST, Type.VALIDATION,
            "Validation Failed", "답변 내용은 4000자 미만이어야 합니다"),

    // ── 공통 ────────────────────────────────────────────────
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, Type.VALIDATION, "Validation Failed",
            "요청 형식이 올바르지 않습니다"),
    /**
     * AC-28 — 5xx 는 이 고정 안내문만 내보낸다. 예외 메시지·스택·테이블명·컬럼명·제약조건명을
     * 어떤 형태로도 붙이지 않는다.
     */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, Type.INTERNAL, "Internal Server Error",
            "일시적 오류가 발생했습니다");

    private static final class Type {
        static final String AUTHENTICATION = "/errors/authentication";
        static final String AUTHORIZATION = "/errors/authorization";
        static final String VALIDATION = "/errors/validation";
        static final String NOT_FOUND = "/errors/not-found";
        static final String CONFLICT = "/errors/conflict";
        static final String RATE_LIMIT = "/errors/rate-limit";
        static final String INTERNAL = "/errors/internal";
    }

    private final HttpStatus status;
    private final String type;
    private final String title;
    private final String detail;

    ErrorCode(HttpStatus status, String type, String title, String detail) {
        this.status = status;
        this.type = type;
        this.title = title;
        this.detail = detail;
    }

    public HttpStatus status() {
        return status;
    }

    public String type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }
}
