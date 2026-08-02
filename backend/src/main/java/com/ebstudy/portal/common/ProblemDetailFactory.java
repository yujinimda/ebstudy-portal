package com.ebstudy.portal.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
// json을 만들게 되는 공장
// 예시: 409 { "code": "USER_ID_DUPLICATED", "detail": "이미 사용 중인 아이디입니다" }
/**
 * ★ 오류 응답을 만드는 <b>유일한 자리</b> (research.md 15 · plan.md 원칙 IV).
 *
 * <p>{@code @RestControllerAdvice} 하나로는 부족하다 — Spring Security 필터 체인의 401/403 은
 * 컨트롤러에 닿기 전에 발생한다. 그래서 <b>생성기 1개 + 어댑터 3개</b> 구조를 쓴다:
 * <ol>
 *   <li>{@link GlobalExceptionHandler} (컨트롤러 이후)</li>
 *   <li>{@code AuthenticationEntryPoint} (401)</li>
 *   <li>{@code AccessDeniedHandler} (403)</li>
 * </ol>
 * 셋이 <b>모두 이 클래스를 공유</b>하므로 {@code AC-27}(모든 오류에 traceId)과
 * {@code AC-2}/{@code AC-3}/{@code AC-32}(응답 동일성)가 한 곳에서 보장된다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProblemDetailFactory {

    private final ObjectMapper objectMapper;

    public ProblemDetail create(ErrorCode code, String instance, List<ApiException.FieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatus(code.status());
        problem.setType(URI.create(code.type()));
        problem.setTitle(code.title());
        problem.setDetail(code.detail());
        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }
        problem.setProperty("code", code.name());
        problem.setProperty("traceId", TraceIdFilter.currentTraceId());
        // AC-2·AC-3·AC-32 — errors 는 "유무"까지 같아야 한다. 비어 있으면 필드를 만들지 않는다.
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", errors.stream()
                    .map(e -> Map.of("field", e.field(), "reason", e.reason()))
                    .toList());
        }
        return problem;
    }

    /** 필터 체인(Security)에서 직접 응답을 써야 하는 경로. 위 {@link #create}와 같은 본문이 나간다. */
    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code)
            throws IOException {
        // ★ AC-27 — Security 필터 체인이 만드는 401·403 은 GlobalExceptionHandler 를 거치지
        //   않는다(컨트롤러에 닿기 전에 발생한다). 여기서 남기지 않으면 이 경로만
        //   "번호는 주는데 찾을 로그가 없는" 상태가 된다.
        //   traceId 는 TraceIdFilter 가 MDC 에 넣어 두어 자동으로 붙는다.
        log.info("요청 거부 status={} code={} path={}",
                code.status().value(), code.name(), request.getRequestURI());
        ProblemDetail problem = create(code, request.getRequestURI(), List.of());
        response.setStatus(code.status().value());
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
