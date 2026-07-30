package com.ebstudy.portal.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * ★ 조회도 {@code lower()} 로 해야 한다 — 인덱스가 {@code LOWER(username)} 이므로
     * {@code username = ?} 로 찾으면 인덱스를 타지 못하고 무엇보다
     * <b>대소문자가 다른 값을 못 찾는다</b>(data-model.md 물리 설계 2절).
     */
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoringCase(@Param("username") String username);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoringCase(@Param("username") String username);

    boolean existsByRole(Role role);

    Optional<User> findFirstByRole(Role role);
}
