package com.ebstudy.portal.auth;

import com.ebstudy.portal.user.Role;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

// JWT 출입증 발급기 겸 검사기
/**
 * Access 토큰 — JWT. 서명만 검증하고 DB 를 보지 않는다(ADR-001).
 * JWT 라이브러리를 따로 넣지 않고 <b>Spring Security 내장(Nimbus)</b>을 쓴다(research.md 10).
 *
 * <p>Refresh 는 JWT 가 아니다 — {@link RefreshTokenService} 참조.
 */
@Component
@Slf4j
public class JwtIssuer {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtIssuer(AuthProperties properties) {
        SecretKey key = deriveKey(properties.jwtSecret());
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    public String issue(Long userId, String username, Role role, Duration ttl, Instant now) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("role", role.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** 만료·위조는 <b>빈 값</b>으로 돌려준다 — 그 뒤 401 AUTH_REQUIRED 가 나가고 프론트가 재발급한다. */
    public Optional<AuthenticatedUser> verify(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            Long userId = ((Number) jwt.getClaim("uid")).longValue();
            Role role = Role.valueOf(jwt.getClaimAsString("role"));
            return Optional.of(new AuthenticatedUser(userId, jwt.getSubject(), role));
        } catch (RuntimeException ex) {
            // 토큰 값을 로그에 남기지 않는다(FR-026 · AC-36)
            return Optional.empty();
        }
    }

    /**
     * 어떤 길이의 시크릿이 와도 HS256 이 요구하는 256비트 키가 되도록 SHA-256 으로 유도한다.
     * 비어 있으면 <b>임시 키를 만들고 경고</b>한다 — 로컬에서 뜨긴 하지만 재시작하면 전원 로그아웃된다.
     */
    private static SecretKey deriveKey(String secret) {
        byte[] material;
        if (secret == null || secret.isBlank()) {
            material = new byte[32];
            new SecureRandom().nextBytes(material);
            log.warn("JWT_SECRET 이 비어 있어 임시 서명 키를 생성했다. "
                    + "재시작하면 발급된 Access 토큰이 전부 무효가 된다. 운영에서는 반드시 설정한다.");
        } else {
            try {
                material = MessageDigest.getInstance("SHA-256")
                        .digest(secret.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 을 찾을 수 없다", ex);
            }
        }
        return new SecretKeySpec(material, "HmacSHA256");
    }
}
