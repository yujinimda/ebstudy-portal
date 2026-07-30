package com.ebstudy.portal.auth.ratelimit;

/**
 * "얼마나 지연할지 계산" — 순수 함수. "실제로 지연"은 {@link Delayer} 가 한다.
 *
 * <p>둘을 나눈 이유(research.md 6): {@code Clock} 은 <b>기다리는 행위를 대체하지 못한다.</b>
 * 그래서 계산은 단위 테스트로 검증하고 실제 대기는 테스트에서 무동작 구현으로 갈아 끼운다.
 *
 * <p>확정 임계값: 1~2회 지연 없음 → 3회 1초 → 4회 2초 → 5회 4초 → 6회 이상 429(지연 없이 즉시).
 */
public final class LoginDelayPolicy {

    private LoginDelayPolicy() {
    }

    public static long delayMillis(int failCount, int delayThreshold, int blockThreshold) {
        if (failCount >= blockThreshold) {
            // 차단된 요청까지 붙잡고 있을 이유가 없다 — 지연 없이 즉시 429 (research.md 6)
            return 0L;
        }
        if (failCount < delayThreshold) {
            return 0L;
        }
        int steps = failCount - delayThreshold;
        return 1000L << steps;
    }
}
