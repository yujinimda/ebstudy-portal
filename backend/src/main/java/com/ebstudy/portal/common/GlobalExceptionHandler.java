package com.ebstudy.portal.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 어댑터 1/3 — 컨트롤러 이후에 발생한 예외 (research.md 15).
 *
 * <p>응답 본문은 {@link ProblemDetailFactory} 하나가 만든다. 여기서 문구를 만들지 않는다.
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final ProblemDetailFactory problems;

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
     * 서블릿 멀티파트 한도({@code spring.servlet.multipart.max-file-size})를 넘은 업로드.
     *
     * <p><b>왜 따로 잡는가.</b> 이 예외는 {@code AttachmentValidator} 가 돌기 <b>전에</b>
     * 서블릿 컨테이너가 던진다. 그래서 게시판별 크기 정책(자유 2MB · 갤러리 1MB)에 걸린
     * 파일과 달리 아래 {@link #handleUnexpected} 로 떨어져 <b>500 · "일시적 오류가
     * 발생했습니다"</b> 가 나갔다. 사용자는 사진이 크다는 것을 알 수 없고, 서버는 사용자가
     * 만든 정상적인 실패를 ERROR 로그로 쌓는다.
     *
     * <p>한도를 아무리 올려도 그 위는 늘 남으므로 설정이 아니라 <b>핸들러</b>로 막아야 한다.
     * 정책 안쪽 초과와 같은 코드({@code ATTACHMENT_TOO_LARGE} · 400)로 응답을 맞춘다 —
     * 화면은 "파일이 너무 큽니다" 하나만 처리하면 된다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleUploadTooLarge(MaxUploadSizeExceededException ex,
            HttpServletRequest request) {
        // 로그는 respond() 가 남긴다 — 여기서 또 찍으면 한 요청에 두 줄이 된다
        return respond(ErrorCode.ATTACHMENT_TOO_LARGE, List.of(), request);
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
        logClientError(code, errors, request);
        ProblemDetail body = problems.create(code, request.getRequestURI(), errors);
        return ResponseEntity.status(code.status())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body(body);
    }

    /**
     * ★ AC-27 — 4xx 도 <b>한 줄은 남긴다.</b>
     *
     * <p><b>왜 필요한가.</b> 오류 응답에는 전부 {@code traceId} 가 실려 나가고 화면도 그것을
     * 보여준다. 그런데 5xx 만 로그를 남기면 <b>4xx 는 번호를 주고도 찾을 것이 없다</b> —
     * 사용자가 <i>"분류를 선택해 주세요, 789025e2f520"</i> 이라고 말해도 개발자가 할 수 있는
     * 일이 없다. AC-27 이 요구하는 것은 번호의 존재가 아니라
     * <i>"그 값으로 같은 요청의 로그를 찾을 수 있다"</i> 이다.
     *
     * <p><b>왜 {@code INFO} 인가.</b> 4xx 는 대부분 사용자의 정상적인 실수다. {@code WARN}
     * 이상으로 올리면 경보가 울려야 할 자리에 잡음이 쌓여 <b>진짜 신호가 묻힌다.</b>
     * 5xx 만 {@code ERROR} 로 남긴다.
     *
     * <p><b>무엇을 남기지 않는가.</b> 요청 본문·파라미터 값·필드의 <i>사유 문구</i>를 남기지
     * 않는다. 비밀번호·비밀글 비밀번호가 그 경로로 들어오고, 한 번 남은 로그는 지우기 어렵다
     * (FR-026 · AC-36). <b>필드 이름까지만</b> 남긴다 — 어느 칸에서 걸렸는지는 그것으로 충분하다.
     *
     * <p>{@code traceId} 를 직접 찍지 않는 것은 {@code TraceIdFilter} 가 MDC 에 넣어 두어
     * 모든 로그 줄에 자동으로 붙기 때문이다. 여기서 또 찍으면 같은 값이 두 번 나온다.
     */
    private void logClientError(ErrorCode code, List<ApiException.FieldError> errors,
            HttpServletRequest request) {
        if (code.status().is5xxServerError()) {
            // 5xx 는 handleUnexpected 가 스택 추적과 함께 ERROR 로 이미 남겼다
            return;
        }
        if (errors.isEmpty()) {
            log.info("요청 실패 status={} code={} path={}",
                    code.status().value(), code.name(), request.getRequestURI());
            return;
        }
        log.info("요청 실패 status={} code={} path={} fields={}",
                code.status().value(), code.name(), request.getRequestURI(),
                errors.stream().map(ApiException.FieldError::field).toList());
    }
}
