package com.ebstudy.portal.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사용불가 아이디 — FR-028. 코드가 아니라 데이터다(관리 화면은 004).
 * 초기 목록은 마이그레이션이 넣는다(예약어는 비밀이 아니므로 커밋해도 안전).
 */
@Entity
@Table(name = "reserved_usernames")
public class ReservedUsername {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    protected ReservedUsername() {
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }
}
