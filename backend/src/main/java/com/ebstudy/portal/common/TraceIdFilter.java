package com.ebstudy.portal.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 traceId 를 만들어 MDC 에 넣는다 — process.md 4.9 원칙 2.
 *
 * <p>오류 응답의 {@code traceId} 와 <b>같은 값</b>이어야 한다({@code AC-27}).
 * {@link ProblemDetailFactory} 가 이 MDC 값을 읽으므로 값이 갈라지는 자리가 없다.
 *
 * <p>Security 필터보다 먼저 돌아야 한다 — 필터 체인이 만드는 401/403 응답에도 traceId 가
 * 붙어야 하기 때문이다.
 */
@Component
@Order(Integer.MIN_VALUE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "traceId";
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        MDC.put(MDC_KEY, newTraceId());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String newTraceId() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = HEX[(bytes[i] >> 4) & 0xF];
            out[i * 2 + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(out);
    }

    public static String currentTraceId() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null ? "unknown" : traceId;
    }
}
