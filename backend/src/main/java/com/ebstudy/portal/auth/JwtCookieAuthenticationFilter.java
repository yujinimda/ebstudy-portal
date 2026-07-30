package com.ebstudy.portal.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Access 쿠키를 읽어 인증 컨텍스트를 만든다. DB 를 보지 않는다(ADR-001).
 *
 * <p>★ 만료된 Access 를 <b>몰래 재발급하지 않는다</b>(research.md 14) — 그냥 인증하지 않고
 * 넘겨서 401 {@code AUTH_REQUIRED} 가 나가게 한다. 재발급은 프론트가
 * {@code POST /api/auth/refresh} 로 명시적으로 한다. 필터가 몰래 갱신하면
 * <b>모든 엔드포인트가 Set-Cookie 를 보낼 수 있게 되어</b> 인증 상태 변화를 추적할 수 없다.
 */
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    private final AuthCookies cookies;
    private final JwtIssuer jwtIssuer;

    public JwtCookieAuthenticationFilter(AuthCookies cookies, JwtIssuer jwtIssuer) {
        this.cookies = cookies;
        this.jwtIssuer = jwtIssuer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            cookies.read(request, cookies.accessName())
                    .flatMap(jwtIssuer::verify)
                    .ifPresent(user -> {
                        var authorities = List.of(
                                new SimpleGrantedAuthority("ROLE_" + user.role().name()));
                        var authentication = new UsernamePasswordAuthenticationToken(user, null,
                                authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        chain.doFilter(request, response);
    }
}
