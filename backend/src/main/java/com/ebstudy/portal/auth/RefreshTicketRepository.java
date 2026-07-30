package com.ebstudy.portal.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTicketRepository extends JpaRepository<RefreshTicket, Long> {

    Optional<RefreshTicket> findByTokenHash(byte[] tokenHash);

    /**
     * 만료 행 청소(하루 1회) — <b>배치로 쪼개</b> 지운다. 한 트랜잭션에서 전량을 지우면
     * 잠금이 커지고 그 시간 동안 로그인·재발급이 같은 테이블에서 경합한다(물리 설계 5절).
     */
    @Modifying
    @Query(value = """
            delete from refresh_tickets
             where id in (select id from refresh_tickets where expires_at < :now limit :batchSize)
            """, nativeQuery = true)
    int deleteExpiredBatch(@Param("now") OffsetDateTime now, @Param("batchSize") int batchSize);

    long countByUserId(Long userId);
}
