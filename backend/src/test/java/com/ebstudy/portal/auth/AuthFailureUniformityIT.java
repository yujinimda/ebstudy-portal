package com.ebstudy.portal.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebstudy.portal.support.ApiClient;
import com.ebstudy.portal.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * ★ 이 스토리에서 가장 깨지기 쉬운 것 — <b>실패는 전부 같은 얼굴이어야 한다.</b>
 *
 * <p>각각을 따로 검증하는 테스트만으로는 부족하다. 세 응답이 각자 맞아도 <b>서로 다를 수</b> 있고,
 * 하나만 달라도 계정 열거가 뚫린다. 그래서 <b>세 응답을 서로 비교</b>한다.
 *
 * <p>비교 대상은 research.md 15 의 표를 그대로 쓴다 —
 * 같아야 하는 것: 상태코드 · Content-Type · type · title · code · detail · errors 유무 · 쿠키 동작.
 * 달라도 되는 것: traceId(요청마다 다른 것이 정상) · instance(요청 경로 자체).
 */
class AuthFailureUniformityIT extends IntegrationTestBase {

    @Test
    @DisplayName("AC-2 · AC-3 · AC-32 — 세 실패 응답이 서로 완전히 같다")
    void ac2_ac3_ac32_responsesAreIdentical() {
        ApiClient client = newClient();
        assertThat(signup(client, "hong01", VALID_PASSWORD, "홍길동").status()).isEqualTo(201);
        client.clearCookies();

        // AC-2 — 존재하는 계정 + 틀린 비밀번호
        ApiClient.Response wrongPassword = login(newClient(), "hong01", "WrongPass9876");
        // AC-3 — 존재하지 않는 아이디
        ApiClient.Response noSuchUser = login(newClient(), "nosuchuser", VALID_PASSWORD);
        // AC-32 — 관리자 진입점에 USER 계정 + 정확한 비밀번호
        ApiClient.Response userAtAdminEntry = adminLogin(newClient(), "hong01", VALID_PASSWORD);

        for (ApiClient.Response response : new ApiClient.Response[] {
                wrongPassword, noSuchUser, userAtAdminEntry }) {
            assertThat(response.status()).isEqualTo(401);
            assertThat(response.header("Content-Type").orElseThrow())
                    .startsWith("application/problem+json");
            JsonNode body = json(response.body());
            assertThat(body.get("code").asString()).isEqualTo("AUTH_INVALID_CREDENTIALS");
            assertThat(body.get("detail").asString()).isEqualTo("아이디 또는 비밀번호를 확인해 주세요");
            // errors 는 "유무"까지 같아야 한다 — 셋 다 없다
            assertThat(body.has("errors")).isFalse();
            // AC-27 — 모든 오류 응답에 추적 식별자가 있다
            assertThat(body.get("traceId").asString()).isNotBlank();
            // 셋 다 Set-Cookie 를 보내지 않는다
            assertThat(response.setCookies()).isEmpty();
            assertThat(response.header("WWW-Authenticate")).isEmpty();
        }

        JsonNode a = json(wrongPassword.body());
        JsonNode b = json(noSuchUser.body());
        JsonNode c = json(userAtAdminEntry.body());

        for (String field : new String[] { "type", "title", "status", "detail", "code" }) {
            assertThat(a.get(field)).as("AC-2 vs AC-3: " + field).isEqualTo(b.get(field));
            assertThat(a.get(field)).as("AC-2 vs AC-32: " + field).isEqualTo(c.get(field));
        }
        // 필드 집합 자체가 같아야 한다 — 한쪽에만 있는 필드가 힌트가 된다
        assertThat(fieldNames(a)).isEqualTo(fieldNames(b));
        assertThat(fieldNames(a)).isEqualTo(fieldNames(c));

        // 달라도 되는 것: traceId(요청마다 다르다) · instance(경로)
        assertThat(a.get("traceId").asString()).isNotEqualTo(b.get("traceId").asString());
        assertThat(c.get("instance").asString()).isEqualTo("/api/admin/auth/login");
    }

    private static java.util.List<String> fieldNames(JsonNode node) {
        java.util.List<String> names = new java.util.ArrayList<>();
        node.propertyNames().forEach(names::add);
        java.util.Collections.sort(names);
        return names;
    }
}
