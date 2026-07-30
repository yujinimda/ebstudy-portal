package com.ebstudy.portal.auth.ratelimit;

import com.ebstudy.portal.auth.AuthProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * FR-027 · AC-29 — <b>계정 단위</b> 실패 카운터. 서버 메모리에 둔다(research.md 5).
 *
 * <p><b>계정을 잠그지 않는다</b>(FR-027 MUST NOT). 아이디만 아는 공격자가 타인을 서비스에서
 * 쫓아낼 수 있고, 001 에는 비밀번호 찾기가 없어 해제 수단도 없다.
 * 차단 시간이 지나면 올바른 비밀번호로 정상 로그인된다.
 */
@Service
public class LoginAttemptService {

    private final LruCache<String, Attempt> cache;
    private final AuthProperties.Fail config;

    public LoginAttemptService(AuthProperties properties) {
        this.config = properties.fail();
        this.cache = new LruCache<>(config.cacheMaxEntries());
    }

    /** 결과: 429 로 막을지, 아니면 얼마나 지연시킬지. */
    public record Decision(boolean blocked, long delayMillis) {
    }

    public boolean isBlocked(String username, Instant now) {
        Attempt attempt = cache.get(key(username));
        return attempt != null && attempt.isBlocked(now);
    }

    /** 실패를 기록하고 이번 응답을 어떻게 할지 판정한다. */
    public Decision recordFailure(String username, Instant now) {
        Attempt attempt = cache.computeIfAbsent(key(username), k -> new Attempt());
        return attempt.fail(now, config);
    }

    /** 로그인 성공 시 <b>즉시 초기화</b>한다(research.md 6). */
    public void reset(String username) {
        cache.remove(key(username));
    }

    public void clearAll() {
        cache.clear();
    }

    private static String key(String username) {
        return username == null ? "" : username.toLowerCase(Locale.ROOT);
    }

    private static final class Attempt {
        private int count;
        private Instant lastFailure;
        private Instant blockedUntil;

        synchronized boolean isBlocked(Instant now) {
            return blockedUntil != null && blockedUntil.isAfter(now);
        }

        synchronized Decision fail(Instant now, AuthProperties.Fail config) {
            if (blockedUntil != null && blockedUntil.isAfter(now)) {
                return new Decision(true, 0L);
            }
            Duration window = config.window();
            if (lastFailure == null || Duration.between(lastFailure, now).compareTo(window) > 0) {
                count = 0;
                blockedUntil = null;
            }
            count++;
            lastFailure = now;
            if (count >= config.blockThreshold()) {
                blockedUntil = now.plus(config.blockDuration());
                return new Decision(true, 0L);
            }
            return new Decision(false, LoginDelayPolicy.delayMillis(count,
                    config.delayThreshold(), config.blockThreshold()));
        }
    }
}
