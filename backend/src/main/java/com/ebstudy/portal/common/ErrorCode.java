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
