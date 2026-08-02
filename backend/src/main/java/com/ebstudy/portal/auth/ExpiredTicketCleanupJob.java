package com.ebstudy.portal.auth;

import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료 티켓 청소 — 하루 1회(data-model.md 물리 설계 5절 · research.md 7).
 *
 * <p>DB 에는 TTL 이 없으므로 테이블을 만들면 청소 책임이 따라온다. 로그인 1회당 행 1개가 쌓이고
 * 지워지지 않으면 <b>조용히 나빠진다</b>.
 *
 * <p><b>배치로 쪼개 반복</b> 삭제한다 — 한 트랜잭션에서 전량을 지우면 잠금과 WAL 이 커지고
 * 그 시간 동안 로그인·재발급이 같은 테이블에서 경합한다.
 *
 * <p><b>전제: 서버 1대.</b> 방아쇠는 서버가 2대 이상이 되는 시점(그때 단일 실행 보장이 필요하다).
 */
@Component
@Slf4j
public class ExpiredTicketCleanupJob {

    private static final int MAX_BATCHES = 1000;

    private final RefreshTokenService refreshTokens;
    private final int batchSize;

    public ExpiredTicketCleanupJob(RefreshTokenService refreshTokens, AuthProperties properties) {
        this.refreshTokens = refreshTokens;
        this.batchSize = properties.cleanup().batchSize();
    }

    @Scheduled(cron = "0 30 4 * * *")
    public void cleanup() {
        OffsetDateTime now = OffsetDateTime.now();
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int deleted = refreshTokens.deleteExpired(now, batchSize);
            total += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        if (total > 0) {
            log.info("만료된 재발급 티켓 {}건을 삭제했다.", total);
        }
    }
}
