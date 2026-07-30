package com.ebstudy.portal.auth.ratelimit;

import com.ebstudy.portal.auth.AuthProperties;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * FR-030 · AC-30 — <b>클라이언트 단위</b> 중복확인 빈도 제한(분당 10회).
 *
 * <p>중복확인은 설계상 아이디 존재 여부를 그대로 알려준다. 제한이 없으면 {@code AC-3} 으로 막은
 * 계정 열거가 이 경로로 뚫려 가입자 명단을 뽑을 수 있다.
 */
@Service
public class CheckIdRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final LruCache<String, Window> cache;
    private final int limitPerMinute;

    public CheckIdRateLimiter(AuthProperties properties) {
        this.limitPerMinute = properties.checkId().ratePerMinute();
        this.cache = new LruCache<>(properties.checkId().cacheMaxEntries());
    }

    /** true 면 허용, false 면 429. */
    public boolean tryAcquire(String clientKey, Instant now) {
        Window window = cache.computeIfAbsent(clientKey == null ? "" : clientKey, k -> new Window());
        return window.tryAcquire(now, limitPerMinute);
    }

    public void clearAll() {
        cache.clear();
    }

    private static final class Window {
        private Instant startedAt;
        private int count;

        synchronized boolean tryAcquire(Instant now, int limit) {
            if (startedAt == null || Duration.between(startedAt, now).compareTo(WINDOW) > 0) {
                startedAt = now;
                count = 0;
            }
            count++;
            return count <= limit;
        }
    }
}
