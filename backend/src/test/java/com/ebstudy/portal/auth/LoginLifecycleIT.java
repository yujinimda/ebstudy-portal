package com.ebstudy.portal.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebstudy.portal.support.ApiClient;
import com.ebstudy.portal.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** AC-1 · AC-5 · AC-9 · AC-35 — 로그인 → /api/me → 로그아웃 → 재발급 거부. */
class LoginLifecycleIT extends IntegrationTestBase {

    @Test
    @DisplayName("AC-1 — 자격증명은 httpOnly 쿠키로만 오고 응답 본문에 토큰이 없다")
    void ac1_credentialsOnlyInHttpOnlyCookies() {
        ApiClient client = newClient();
        signup(client, "hong01", VALID_PASSWORD, "홍길동");
        client.clearCookies();

        ApiClient.Response response = login(client, "hong01", VALID_PASSWORD);

        assertThat(response.status()).isEqualTo(200);
        String access = response.setCookie("ACCESS_TOKEN").orElseThrow();
        String refresh = response.setCookie("REFRESH_TOKEN").orElseThrow();
        assertThat(access).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/");
        assertThat(refresh).contains("HttpOnly").contains("SameSite=Lax")
                // Refresh 는 재발급·로그아웃 경로에만 실린다 — 모든 요청에 붙으면 노출 표면이 넓어진다
                .contains("Path=/api/auth");

        // 본문에 토큰 문자열이 없다
        String accessValue = client.cookie("ACCESS_TOKEN").orElseThrow();
        String refreshValue = client.cookie("REFRESH_TOKEN").orElseThrow();
        assertThat(response.body()).doesNotContain(accessValue).doesNotContain(refreshValue);
        JsonNode body = json(response.body());
        assertThat(body.get("username").asString()).isEqualTo("hong01");
        assertThat(body.get("role").asString()).isEqualTo("USER");
    }

    @Test
    @DisplayName("AC-9 — 저장된 비밀번호는 평문이 아니다")
    void ac9_passwordIsNotStoredInPlainText() {
        ApiClient client = newClient();
        signup(client, "hong01", VALID_PASSWORD, "홍길동");

        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE username = 'hong01'", String.class);

        assertThat(hash).isNotNull().isNotEqualTo(VALID_PASSWORD).doesNotContain(VALID_PASSWORD)
                .startsWith("$2");
    }

    @Test
    @DisplayName("AC-1·AC-5·AC-35 — 로그인 → /api/me → 로그아웃 → 그 티켓만 거부되고 다른 기기는 살아 있다")
    void ac5_ac35_logoutRevokesOnlyTheRequestingDevice() {
        ApiClient signupClient = newClient();
        signup(signupClient, "hong01", VALID_PASSWORD, "홍길동");

        ApiClient deviceA = newClient();
        ApiClient deviceB = newClient();
        assertThat(login(deviceA, "hong01", VALID_PASSWORD).status()).isEqualTo(200);
        assertThat(login(deviceB, "hong01", VALID_PASSWORD).status()).isEqualTo(200);

        // 로그인 1회 = 티켓 1개
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_tickets", Long.class))
                .isEqualTo(2L);

        ApiClient.Response me = deviceA.get("/api/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(json(me.body()).get("name").asString()).isEqualTo("홍길동");

        // 재발급이 정상 동작한다 (AC-4 의 백엔드 몫 — 유효한 Refresh 면 200)
        assertThat(deviceA.post("/api/auth/refresh", "").status()).isEqualTo(200);

        ApiClient.Response logout = deviceA.post("/api/auth/logout", "");
        assertThat(logout.status()).isEqualTo(204);
        // 쿠키를 설정할 때와 같은 속성으로 지운다 — 하나라도 다르면 브라우저가 지우지 않는다
        assertThat(logout.setCookie("ACCESS_TOKEN").orElseThrow())
                .contains("Max-Age=0").contains("HttpOnly").contains("SameSite=Lax");
        assertThat(logout.setCookie("REFRESH_TOKEN").orElseThrow())
                .contains("Max-Age=0").contains("Path=/api/auth");
        assertThat(deviceA.cookie("REFRESH_TOKEN")).isEmpty();

        // AC-35 — 다른 기기는 정상적으로 재발급된다
        assertThat(deviceB.post("/api/auth/refresh", "").status()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tickets WHERE revoked = true", Long.class))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("AC-5 — 로그아웃한 Refresh 로 재발급하면 401 AUTH_REFRESH_INVALID 다")
    void ac5_revokedTicketIsRejected() {
        ApiClient signupClient = newClient();
        signup(signupClient, "hong01", VALID_PASSWORD, "홍길동");

        ApiClient device = newClient();
        login(device, "hong01", VALID_PASSWORD);
        String refreshToken = device.cookie("REFRESH_TOKEN").orElseThrow();
        assertThat(device.post("/api/auth/logout", "").status()).isEqualTo(204);

        // 브라우저가 쿠키를 지웠어도 값을 알고 있는 공격자를 가정한다 — 서버가 막아야 한다
        ApiClient attacker = newClient();
        attacker.putCookie("REFRESH_TOKEN", refreshToken);
        ApiClient.Response response = attacker.post("/api/auth/refresh", "");

        assertThat(response.status()).isEqualTo(401);
        assertThat(json(response.body()).get("code").asString()).isEqualTo("AUTH_REFRESH_INVALID");
        assertThat(response.setCookie("REFRESH_TOKEN").orElseThrow()).contains("Max-Age=0");
    }
}
