package com.ebstudy.portal.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservedUsernameRepository extends JpaRepository<ReservedUsername, Long> {

    /**
     * AC-13 — 금지 판정도 대소문자를 무시한다. {@code admin} 이 금지면 {@code Admin}·{@code ADMIN} 도
     * 거부된다(data-model.md — codex 검증에서 실제로 뚫렸던 구멍).
     */
    @Query("select count(r) > 0 from ReservedUsername r where lower(r.username) = lower(:username)")
    boolean existsIgnoringCase(@Param("username") String username);
}
