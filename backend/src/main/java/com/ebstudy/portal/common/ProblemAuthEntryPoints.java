package com.ebstudy.portal.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 어댑터 2/3 · 3/3 — Security 필터 체인이 만드는 401·403 (research.md 15).
 *
 * <p>이 두 응답은 컨트롤러에 닿지 않으므로 {@code @RestControllerAdvice} 가 잡지 못한다.
 * 같은 {@link ProblemDetailFactory} 를 쓰는 것이 {@code AC-21}·{@code AC-22} 의 응답 형식이
 * 다른 오류와 갈라지지 않게 하는 장치다.
 */
@Component
public class ProblemAuthEntryPoints {

    private final ProblemDetailFactory problems;

    public ProblemAuthEntryPoints(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    /** AC-21 — 미인증. */
    public AuthenticationEntryPoint entryPoint() {
        return new AuthenticationEntryPoint() {
            @Override
            public void commence(HttpServletRequest request, HttpServletResponse response,
                    AuthenticationException authException) throws IOException {
                problems.write(request, response, ErrorCode.AUTH_REQUIRED);
            }
        };
    }

    /** AC-22 · AC-26 — 인증은 됐고 권한이 없다. */
    public AccessDeniedHandler accessDeniedHandler() {
        return new AccessDeniedHandler() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response,
                    AccessDeniedException accessDeniedException) throws IOException {
                problems.write(request, response, ErrorCode.AUTH_FORBIDDEN);
            }
        };
    }
}
