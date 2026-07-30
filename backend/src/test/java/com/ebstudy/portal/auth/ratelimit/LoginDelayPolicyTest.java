package com.ebstudy.portal.auth.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC-29 의 <b>계산 부분</b>만 단위로 검증한다 — "실제로 기다리는 행위"는 {@link Delayer} 가 하고
 * 테스트에서는 무동작으로 갈아 끼운다(research.md 6: {@code Clock} 은 대기를 대체하지 못한다).
 */
class LoginDelayPolicyTest {

    @Test
    @DisplayName("AC-29 — 1~2회 지연 없음 → 3회 1초 → 4회 2초 → 5회 4초 → 6회 이상 지연 없이 429")
    void delayGrowsThenStopsAtBlockThreshold() {
        assertThat(LoginDelayPolicy.delayMillis(1, 3, 6)).isZero();
        assertThat(LoginDelayPolicy.delayMillis(2, 3, 6)).isZero();
        assertThat(LoginDelayPolicy.delayMillis(3, 3, 6)).isEqualTo(1000L);
        assertThat(LoginDelayPolicy.delayMillis(4, 3, 6)).isEqualTo(2000L);
        assertThat(LoginDelayPolicy.delayMillis(5, 3, 6)).isEqualTo(4000L);
        // 차단된 요청까지 붙잡고 있으면 방어 장치가 자원 고갈 수단이 된다
        assertThat(LoginDelayPolicy.delayMillis(6, 3, 6)).isZero();
        assertThat(LoginDelayPolicy.delayMillis(20, 3, 6)).isZero();
    }
}
