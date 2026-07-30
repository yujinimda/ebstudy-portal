package com.ebstudy.portal.auth.ratelimit;

import com.ebstudy.portal.auth.AuthProperties;
import org.springframework.stereotype.Component;

/**
 * 실제 대기. <b>반드시 트랜잭션 밖에서</b> 호출한다 — 안에서 기다리면 DB 커넥션과 잠금을
 * 붙잡고 커넥션 풀은 가상 스레드와 무관하게 유한하다(research.md 6 정정).
 *
 * <p>가상 스레드({@code spring.threads.virtual.enabled=true})를 켜므로 대기 중 OS 스레드를
 * 점유하지 않는다.
 */
@Component
public class Delayer {

    private final boolean enabled;

    public Delayer(AuthProperties properties) {
        this.enabled = properties.fail().delayEnabled();
    }

    public void delay(long millis) {
        if (!enabled || millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
