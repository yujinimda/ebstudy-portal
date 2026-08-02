package com.ebstudy.portal.auth;

import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import com.ebstudy.portal.user.Role;
import com.ebstudy.portal.user.User;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh 자격증명 — <b>256비트 난수</b>이고 JWT 가 아니다(research.md 3).
 * DB 에는 SHA-256 해시만 둔다. 서버가 보관하는 이유는 <b>로그아웃을 성립시키는 것 하나</b>다(ADR-001).
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTicketRepository tickets;
    private final AuthProperties properties;

    public Duration refreshTtl(Role role) {
        return role == Role.ADMIN ? properties.admin().refreshTtl() : properties.user().refreshTtl();
    }

    public Duration accessTtl(Role role) {
        return role == Role.ADMIN ? properties.admin().accessTtl() : properties.user().accessTtl();
    }

    /** 로그인 1회 = 티켓 1개. 원문은 쿠키로만 나가고 저장되지 않는다. */
    @Transactional
    public String issue(User user, OffsetDateTime now) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = BASE64URL.encodeToString(raw);
        tickets.save(RefreshTicket.issue(user, sha256(token),
                now.plus(refreshTtl(user.getRole())), now));
        return token;
    }

    /**
     * AC-5 · AC-6 — 무효화(로그아웃)와 만료를 <b>다른 code</b> 로 구분한다.
     * 사용자에게 보여줄 안내가 다르고, 계정 존재 여부와 무관하므로 실패 응답 통일 원칙과 충돌하지 않는다.
     */
    @Transactional(readOnly = true)
    public User validate(String token, OffsetDateTime now) {
        RefreshTicket ticket = tickets.findByTokenHash(sha256(token))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REFRESH_INVALID));
        if (ticket.isRevoked()) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        if (ticket.getExpiresAt().isBefore(now)) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_EXPIRED);
        }
        User user = ticket.getUser();
        user.getUsername(); // 지연 로딩 프록시를 트랜잭션 안에서 초기화한다
        return user;
    }

    /**
     * FR-015 · AC-35 — <b>이 요청이 제시한 해시로 찾은 행 하나</b>만 무효화한다.
     * {@code user_id = ?} 로 지우면 다른 기기가 전부 튕겨 나간다.
     */
    @Transactional
    public void revoke(String token) {
        Optional<RefreshTicket> found = tickets.findByTokenHash(sha256(token));
        found.ifPresent(RefreshTicket::revoke);
    }

    @Transactional
    public int deleteExpired(OffsetDateTime now, int batchSize) {
        return tickets.deleteExpiredBatch(now, batchSize);
    }

    static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 을 찾을 수 없다", ex);
        }
    }
}
