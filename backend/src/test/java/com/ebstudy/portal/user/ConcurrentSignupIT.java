package com.ebstudy.portal.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebstudy.portal.support.ApiClient;
import com.ebstudy.portal.support.IntegrationTestBase;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 동시 가입 경합 — spec.md Edge Cases · AC-10.
 *
 * <p>★ <b>대소문자가 다른 아이디({@code Kim01} vs {@code kim01})로 경합시키는 것이 핵심이다.</b>
 * {@code ddl-auto=validate} 는 <b>함수 유니크 인덱스를 검증하지 않으므로</b>
 * {@code LOWER(username)} 인덱스가 실제로 있는지 확인하는 <b>유일한 장치</b>가 이 테스트다
 * (ADR-003 리스크 1 · data-model.md 물리 설계 2절).
 *
 * <p>격리 예외: 실제 커밋이 경합해야 하므로 트랜잭션 롤백을 쓰지 않는다(test-strategy.md 5.2).
 */
class ConcurrentSignupIT extends IntegrationTestBase {

    @Test
    @DisplayName("AC-10 — Kim01 과 kim01 이 동시에 가입하면 하나만 성공하고 다른 하나는 409 다")
    void ac10_caseInsensitiveConcurrentSignupLeavesOnlyOne() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<ApiClient.Response> upper = attempt(barrier, "Kim01");
            Callable<ApiClient.Response> lower = attempt(barrier, "kim01");

            List<Future<ApiClient.Response>> results = pool.invokeAll(List.of(upper, lower));
            int created = 0;
            int duplicated = 0;
            for (Future<ApiClient.Response> future : results) {
                ApiClient.Response response = future.get();
                if (response.status() == 201) {
                    created++;
                } else {
                    duplicated++;
                    assertThat(response.status()).isEqualTo(409);
                    assertThat(json(response.body()).get("code").asString())
                            .isEqualTo("USER_ID_DUPLICATED");
                    // AC-28 — 제약조건명·테이블명이 새지 않는다
                    assertThat(response.body()).doesNotContain("uk_users_username_lower")
                            .doesNotContain("users").doesNotContain("Exception");
                }
            }

            assertThat(created).isEqualTo(1);
            assertThat(duplicated).isEqualTo(1);
        }

        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE lower(username) = 'kim01'", Long.class);
        assertThat(count).isEqualTo(1L);
    }

    private Callable<ApiClient.Response> attempt(CyclicBarrier barrier, String username) {
        return () -> {
            ApiClient client = newClient();
            barrier.await();
            return signup(client, username, "Study1234abcd", "김철수");
        };
    }
}
