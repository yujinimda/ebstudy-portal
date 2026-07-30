package com.ebstudy.portal.auth;

import com.ebstudy.portal.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 재발급 티켓 — "로그인마다 하나"다(기기마다가 아니다, data-model.md).
 *
 * <p>식별 값 원문은 저장하지 않는다. {@code SHA-256} 해시만 담고 그 해시에 유니크 인덱스가 있다
 * (물리 설계 3절). bcrypt 를 쓸 수 없는 이유는 <b>해시로 행을 찾아야</b> 하는데 bcrypt 는
 * 솔트가 매번 달라 {@code WHERE 해시 = ?} 가 성립하지 않기 때문이다.
 */
@Entity
@Table(name = "refresh_tickets")
public class RefreshTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private byte[] tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected RefreshTicket() {
    }

    private RefreshTicket(User user, byte[] tokenHash, OffsetDateTime expiresAt,
            OffsetDateTime createdAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revoked = false;
        this.createdAt = createdAt;
    }

    public static RefreshTicket issue(User user, byte[] tokenHash, OffsetDateTime expiresAt,
            OffsetDateTime createdAt) {
        return new RefreshTicket(user, tokenHash, expiresAt, createdAt);
    }

    /** FR-014 · AC-35 — 이 <b>한 행만</b> 무효화한다. 유저 기준으로 지우면 다른 기기가 튕겨 나간다. */
    public void revoke() {
        this.revoked = true;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }
}
