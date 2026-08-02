package com.ebstudy.portal.auth;

import com.ebstudy.portal.auth.ratelimit.CheckIdRateLimiter;
import com.ebstudy.portal.auth.ratelimit.ClientIpResolver;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import com.ebstudy.portal.user.SignupService;
import com.ebstudy.portal.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** contracts/auth-api.md 1·2·3·5·6번 엔드포인트. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignupService signupService;
    private final AuthService authService;
    private final RefreshTokenService refreshTokens;
    private final AuthCookies cookies;
    private final CheckIdRateLimiter checkIdRateLimiter;
    private final ClientIpResolver clientIpResolver;

    public record SignupRequest(String username, String password, String name) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record UserResponse(String username, String name, String role) {
        public static UserResponse of(User user) {
            return new UserResponse(user.getUsername(), user.getName(), user.getRole().name());
        }
    }

    public record AvailabilityResponse(boolean available) {
    }

    /** 1. AC-8~19 — 모든 검증은 서버에서 한다(FR-002). */
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@RequestBody SignupRequest request) {
        User created = signupService.signup(request.username(), request.password(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.of(created));
    }

    /** 2. AC-20 · AC-30 — 여기서 불가로 답한 아이디는 가입에서도 거부된다. */
    @GetMapping("/check-id")
    public AvailabilityResponse checkId(@RequestParam("username") String username,
            HttpServletRequest request) {
        String clientKey = clientIpResolver.resolve(request);
        if (!checkIdRateLimiter.tryAcquire(clientKey, Instant.now())) {
            throw new ApiException(ErrorCode.CHECK_ID_TOO_MANY_REQUESTS);
        }
        return new AvailabilityResponse(signupService.isAvailable(username));
    }

    /**
     * 3. AC-1 · AC-2 · AC-3 · AC-29.
     * {@code ADMIN} 계정도 이 진입점으로 로그인할 수 있다(계약 3번 — FR-017 하나의 인증 체계).
     * 발급되는 수명은 <b>진입점이 아니라 계정의 역할</b> 기준이다(FR-033).
     */
    @PostMapping("/login")
    public UserResponse login(@RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.Session session = authService.login(request.username(), request.password(), false);
        writeSession(response, session);
        return UserResponse.of(session.user());
    }

    /** 5. AC-4 · AC-5 · AC-6 — 재발급을 부르는 것은 프론트다(research.md 14). */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        String token = cookies.read(request, cookies.refreshName())
                .orElseThrow(() -> {
                    clearCookies(response);
                    return new ApiException(ErrorCode.AUTH_REFRESH_INVALID);
                });
        User user;
        try {
            user = refreshTokens.validate(token, OffsetDateTime.now());
        } catch (ApiException ex) {
            // AC-5 — 무효화된 티켓이면 쿠키를 삭제한다(설정할 때와 같은 속성으로)
            clearCookies(response);
            throw ex;
        }
        cookies.write(response, cookies.access(authService.issueAccessToken(user),
                authService.accessTtl(user.getRole())));
        return ResponseEntity.ok().build();
    }

    /** 6. AC-5 · AC-35 — <b>요청한 기기만</b> 종료한다. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        cookies.read(request, cookies.refreshName()).ifPresent(refreshTokens::revoke);
        clearCookies(response);
        return ResponseEntity.noContent().build();
    }

    private void writeSession(HttpServletResponse response, AuthService.Session session) {
        // AC-1 — 자격증명은 쿠키로만 나간다. 본문에 토큰 문자열을 담지 않는다.
        cookies.write(response,
                cookies.access(session.accessToken(), session.accessTtl()),
                cookies.refresh(session.refreshToken(), session.refreshTtl()));
    }

    private void clearCookies(HttpServletResponse response) {
        cookies.write(response, cookies.clearAccess(), cookies.clearRefresh());
    }
}
