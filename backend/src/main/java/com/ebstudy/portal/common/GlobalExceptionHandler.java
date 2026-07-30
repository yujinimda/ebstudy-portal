package com.ebstudy.portal.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 어댑터 1/3 — 컨트롤러 이후에 발생한 예외 (research.md 15).
 *
 * <p>응답 본문은 {@link ProblemDetailFactory} 하나가 만든다. 여기서 문구를 만들지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        return respond(ex.code(), ex.errors(), request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ProblemDetail> handleMalformed(Exception ex, HttpServletRequest request) {
        // 예외 메시지를 본문에 담지 않는다 — 스프링 기본 메시지는 클래스명·필드 구조를 노출한다
        return respond(ErrorCode.REQUEST_INVALID, List.of(), request);
    }

    /**
     * AC-28 — 예기치 못한 오류. 고정 안내문 + traceId 만 나간다.
     * 스택 추적은 <b>로그에만</b> 남는다(process.md 4.8).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("처리하지 못한 예외 path={}", request.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, List.of(), request);
    }

    private ResponseEntity<ProblemDetail> respond(ErrorCode code,
            List<ApiException.FieldError> errors, HttpServletRequest request) {
        ProblemDetail body = problems.create(code, request.getRequestURI(), errors);
        return ResponseEntity.status(code.status())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body(body);
    }
}
