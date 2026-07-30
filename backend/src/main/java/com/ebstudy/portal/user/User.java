package com.ebstudy.portal.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 유저 — data-model.md 논리 설계 + 물리 설계 5.6.
 *
 * <p>대소문자 무시 유일성은 <b>엔티티가 아니라 스키마</b>가 보장한다
 * ({@code LOWER(username)} 함수 유니크 인덱스, V1 마이그레이션).
 * {@code ddl-auto=validate} 는 함수 인덱스를 검증하지 않으므로
 * 동시 가입 경합 테스트({@code Kim} vs {@code kim})가 그것을 확인하는 유일한 장치다.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FR-003 이 12자 미만이므로 최대 11자. 길이 제약을 DB에도 둔다(물리 설계 5.6). */
    @Column(name = "username", nullable = false, length = 11, updatable = false)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected User() {
    }

    private User(String username, String passwordHash, String name, Role role, OffsetDateTime createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static User create(String username, String passwordHash, String name, Role role,
            OffsetDateTime createdAt) {
        return new User(username, passwordHash, name, role, createdAt);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public Role getRole() {
        return role;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
