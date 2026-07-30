package com.ebstudy.portal.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 쿠키 속성 — contracts/auth-api.md "쿠키 속성" 표를 그대로 옮긴 자리.
 *
 * <table>
 *   <tr><td>{@code HttpOnly}</td><td>켠다 — AC-7 · FR-013. 스크립트가 읽지 못한다</td></tr>
 *   <tr><td>{@code Secure}</td><td>기본 켠다. 로컬 http 만 환경변수로 끈다(반대로 두면 배포에서 잊는다)</td></tr>
 *   <tr><td>{@code SameSite=Lax}</td><td>★ CSRF 1차 방어선. 001 의 상태 변경은 전부 POST 다</td></tr>
 *   <tr><td>{@code Path}</td><td>Access 는 {@code /}, Refresh 는 재발급·로그아웃 경로에만</td></tr>
 * </table>
 *
 * <p><b>삭제할 때 설정할 때와 같은 속성</b>을 쓴다 — 하나라도 다르면 브라우저가 다른 쿠키로 보아
 * 지워지지 않는다. {@code AC-5}(로그아웃 시 쿠키 삭제)가 조용히 실패하는 대표적 원인이다.
 * 그래서 만들기와 지우기가 <b>같은 메서드</b>를 통과하게 했다.
 */
@Component
public class AuthCookies {

    private final AuthProperties.Cookie config;

    public AuthCookies(AuthProperties properties) {
        this.config = properties.cookie();
    }

    public String accessName() {
        return config.accessName();
    }

    public String refreshName() {
        return config.refreshName();
    }

    public ResponseCookie access(String token, Duration ttl) {
        return build(config.accessName(), token, "/", ttl);
    }

    public ResponseCookie refresh(String token, Duration ttl) {
        return build(config.refreshName(), token, config.refreshPath(), ttl);
    }

    public ResponseCookie clearAccess() {
        return build(config.accessName(), "", "/", Duration.ZERO);
    }

    public ResponseCookie clearRefresh() {
        return build(config.refreshName(), "", config.refreshPath(), Duration.ZERO);
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(config.secure())
                .sameSite(config.sameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    public void write(HttpServletResponse response, ResponseCookie... cookies) {
        for (ResponseCookie cookie : cookies) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
    }

    public Optional<String> read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null
                    && !cookie.getValue().isEmpty()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
