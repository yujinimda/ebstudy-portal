package com.ebstudy.portal.auth;

import com.ebstudy.portal.auth.ratelimit.Delayer;
import com.ebstudy.portal.auth.ratelimit.LoginAttemptService;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import com.ebstudy.portal.user.Role;
import com.ebstudy.portal.user.User;
import com.ebstudy.portal.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 로그인 — FR-010~012 · AC-1~3 · AC-29 · AC-31~33.
 *
 * <p>★ 이 클래스의 존재 이유는 <b>실패가 전부 같은 얼굴이 되게 하는 것</b>이다.
 * {@code AC-2}(틀린 비밀번호) · {@code AC-3}(없는 아이디) · {@code AC-32}(관리자 진입점에 USER)가
 * <b>같은 한 줄</b>에서 같은 예외로 나간다. 세 경로를 각각 던지면 반드시 갈라진다.
 *
 * <p><b>트랜잭션 애노테이션이 없는 것은 의도적이다</b> — 실패 시 지연이 이 안에서 일어나므로
 * 트랜잭션이 열려 있으면 DB 커넥션과 잠금을 붙잡는다(research.md 6 정정).
 * 조회·검증·카운터 갱신은 각각 짧은 트랜잭션으로 끝나고 <b>지연은 그 밖</b>이다.
 */
@Service
public class AuthService {

    /**
     * ★ 없는 아이디에서도 <b>똑같이 해시 검증을 수행</b>하려고 두는 고정 더미 해시.
     * 건너뛰면 그 경로만 빨라져 본문이 같아도 <b>시간차로 아이디 존재 여부가 샌다</b>.
     */
    private final String dummyHash;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptService attempts;
    private final Delayer delayer;
    private final int passwordMaxBytes;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtIssuer jwtIssuer,
            RefreshTokenService refreshTokens, LoginAttemptService attempts, Delayer delayer,
            com.ebstudy.portal.user.SignupPolicy signupPolicy) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.refreshTokens = refreshTokens;
        this.attempts = attempts;
        this.delayer = delayer;
        this.passwordMaxBytes = signupPolicy.passwordMaxBytes();
        this.dummyHash = passwordEncoder.encode("$dummy$never$matches$" + java.util.UUID.randomUUID());
    }

    public record Session(User user, String accessToken, Duration accessTtl, String refreshToken,
            Duration refreshTtl) {
    }

    /**
     * @param adminEntryPoint 관리자 진입점({@code POST /api/admin/auth/login})이면 true.
     *                        ★ 권한은 <b>비밀번호 검증을 끝낸 뒤에</b> 본다. 먼저 보고 조기 반환하면
     *                        {@code AC-32} 경로가 빨라져 시간차로 정보가 샌다.
     */
    public Session login(String username, String password, boolean adminEntryPoint) {
        Instant now = Instant.now();
        String safeUsername = username == null ? "" : username;
        String safePassword = password == null ? "" : password;

        // 차단 상태에서는 지연 없이 즉시 429 (research.md 6)
        if (attempts.isBlocked(safeUsername, now)) {
            throw new ApiException(ErrorCode.AUTH_TOO_MANY_ATTEMPTS);
        }

        Optional<User> found = users.findByUsernameIgnoringCase(safeUsername);
        boolean passwordMatches = verifyPassword(safePassword,
                found.map(User::getPasswordHash).orElse(dummyHash));

        boolean roleAllowed = !adminEntryPoint
                || found.map(user -> user.getRole() == Role.ADMIN).orElse(false);

        if (found.isEmpty() || !passwordMatches || !roleAllowed) {
            LoginAttemptService.Decision decision = attempts.recordFailure(safeUsername, now);
            if (decision.blocked()) {
                throw new ApiException(ErrorCode.AUTH_TOO_MANY_ATTEMPTS);
            }
            // 지연은 트랜잭션 밖. 여기서 열린 트랜잭션이 없다는 것이 이 클래스의 설계 조건이다.
            delayer.delay(decision.delayMillis());
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        attempts.reset(safeUsername);
        User user = found.get();
        return issueSession(user, now);
    }

    public Session issueSession(User user, Instant now) {
        Duration accessTtl = refreshTokens.accessTtl(user.getRole());
        Duration refreshTtl = refreshTokens.refreshTtl(user.getRole());
        String access = jwtIssuer.issue(user.getId(), user.getUsername(), user.getRole(),
                accessTtl, now);
        String refresh = refreshTokens.issue(user, OffsetDateTime.now());
        return new Session(user, access, accessTtl, refresh, refreshTtl);
    }

    /** Access 쿠키만 새로 발급한다 — 재발급은 Refresh 를 회전시키지 않는다(계약 5번). */
    public String issueAccessToken(User user) {
        return jwtIssuer.issue(user.getId(), user.getUsername(), user.getRole(),
                refreshTokens.accessTtl(user.getRole()), Instant.now());
    }

    public Duration accessTtl(Role role) {
        return refreshTokens.accessTtl(role);
    }

    /**
     * bcrypt 는 72바이트를 넘는 입력을 자른다. 자르면 앞 72바이트가 같은 다른 비밀번호로
     * 로그인이 성공한다 → 그래서 <b>거부</b>한다. 단 검증 시간은 그대로 쓴다(시간차 방지).
     */
    private boolean verifyPassword(String rawPassword, String hash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > passwordMaxBytes) {
            passwordEncoder.matches("x", dummyHash);
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }
}
