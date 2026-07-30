package com.ebstudy.portal.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebstudy.portal.support.ApiClient;
import com.ebstudy.portal.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * AC-21 · AC-22 · AC-23 — 관리자 전용 기능의 권한 판정. 대상은 {@code GET /api/admin/me}.
 *
 * <p>401 과 403 은 <b>Security 필터 체인</b>이 만든다(컨트롤러에 닿지 않는다). 그래서 이 테스트가
 * "어댑터 3개가 같은 Problem Details 생성기를 쓰는지"를 함께 확인한다 — traceId·code 가 있는지.
 */
class AuthorizationIT extends IntegrationTestBase {

    @Test
    @DisplayName("AC-21 — 미인증은 401 AUTH_REQUIRED 다")
    void ac21_unauthenticatedIsRejected() {
        ApiClient.Response response = newClient().get("/api/admin/me");

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.header("Content-Type").orElseThrow())
                .startsWith("application/problem+json");
        JsonNode body = json(response.body());
        assertThat(body.get("code").asString()).isEqualTo("AUTH_REQUIRED");
        // AC-27 — 필터가 만든 응답에도 추적 식별자가 있어야 한다
        assertThat(body.get("traceId").asString()).isNotBlank();
    }

    @Test
    @DisplayName("AC-22 — USER 권한은 403 AUTH_FORBIDDEN 이다")
    void ac22_userRoleIsForbidden() {
        ApiClient client = newClient();
        signup(client, "hong01", VALID_PASSWORD, "홍길동");
        client.clearCookies();
        assertThat(login(client, "hong01", VALID_PASSWORD).status()).isEqualTo(200);

        ApiClient.Response response = client.get("/api/admin/me");

        assertThat(response.status()).isEqualTo(403);
        JsonNode body = json(response.body());
        assertThat(body.get("code").asString()).isEqualTo("AUTH_FORBIDDEN");
        assertThat(body.get("traceId").asString()).isNotBlank();
    }

    @Test
    @DisplayName("AC-23 · AC-31 · AC-33 — ADMIN 은 별도 진입점으로 로그인해 200 이고 수명이 더 짧다")
    void ac23_ac31_ac33_adminPassesAndHasShorterLifetime() {
        ApiClient admin = newClient();
        ApiClient.Response adminLogin = adminLogin(admin, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertThat(adminLogin.status()).isEqualTo(200);

        ApiClient.Response response = admin.get("/api/admin/me");
        assertThat(response.status()).isEqualTo(200);
        assertThat(json(response.body()).get("role").asString()).isEqualTo("ADMIN");

        // AC-33 — 관리자 자격증명 수명이 사용자보다 짧다
        ApiClient user = newClient();
        signup(user, "hong01", VALID_PASSWORD, "홍길동");
        user.clearCookies();
        ApiClient.Response userLogin = login(user, "hong01", VALID_PASSWORD);

        long adminAccessMaxAge = maxAge(adminLogin.setCookie("ACCESS_TOKEN").orElseThrow());
        long userAccessMaxAge = maxAge(userLogin.setCookie("ACCESS_TOKEN").orElseThrow());
        long adminRefreshMaxAge = maxAge(adminLogin.setCookie("REFRESH_TOKEN").orElseThrow());
        long userRefreshMaxAge = maxAge(userLogin.setCookie("REFRESH_TOKEN").orElseThrow());

        assertThat(adminAccessMaxAge).isLessThan(userAccessMaxAge);
        assertThat(adminRefreshMaxAge).isLessThan(userRefreshMaxAge);
    }

    private static long maxAge(String setCookie) {
        for (String part : setCookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase().startsWith("max-age=")) {
                return Long.parseLong(trimmed.substring("max-age=".length()));
            }
        }
        throw new IllegalStateException("Max-Age 가 없다: " + setCookie);
    }
}
