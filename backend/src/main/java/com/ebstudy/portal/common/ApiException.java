package com.ebstudy.portal.common;

import java.util.List;

// 에러코드를 실어 넘겨줌
/**
 * 의도한 실패(4xx) — process.md 4.8 의 예외 2분류 중 앞쪽.
 *
 * <p>메시지는 {@link ErrorCode} 가 들고 있고 여기서 만들지 않는다.
 * 예외 메시지를 응답에 쓰지 않으므로 5xx 누수 경로가 생기지 않는다.
 */
 // 던지기만 한다 아무도 안잡으면 계속 위로 올라감
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldError> errors;

    public ApiException(ErrorCode code) {
        this(code, List.of());
    }

    public ApiException(ErrorCode code, List<FieldError> errors) {
        super(code.name());
        this.code = code;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldError> errors() {
        return errors;
    }

    public record FieldError(String field, String reason) {
    }
}
