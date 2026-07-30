package com.ebstudy.portal.auth;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 설정 — 값은 전부 <b>환경변수</b>다(application.yml 의 {@code ${KEY}}).
 * 수명은 데이터가 아니라 설정이다(data-model.md).
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        String jwtSecret,
        Lifetime user,
        Lifetime admin,
        Cookie cookie,
        Fail fail,
        CheckId checkId,
        /** ★ 신뢰할 프록시. 지정 없이 X-Forwarded-For 를 신뢰하면 위조로 빈도 제한이 무력화된다. */
        List<String> trustedProxyCidrs,
        Seed seed,
        Cleanup cleanup) {

    /** FR-033 · AC-33 — 관리자가 반드시 더 짧다. */
    public record Lifetime(Duration accessTtl, Duration refreshTtl) {
    }

    public record Cookie(boolean secure, String accessName, String refreshName, String refreshPath,
            String sameSite) {
    }

    /** FR-027 · AC-29 — 계정 단위 카운터. 계정을 잠그지 않는다. */
    public record Fail(int delayThreshold, int blockThreshold, Duration blockDuration,
            Duration window, int cacheMaxEntries, boolean delayEnabled) {
    }

    /** FR-030 · AC-30 — 클라이언트 단위 카운터. */
    public record CheckId(int ratePerMinute, int cacheMaxEntries) {
    }

    /** FR-022 · FR-023 — 값은 환경변수에만 있다. 저장소에 남기지 않는다. */
    public record Seed(String adminUsername, String adminPassword, String adminName) {
    }

    public record Cleanup(int batchSize) {
    }
}
