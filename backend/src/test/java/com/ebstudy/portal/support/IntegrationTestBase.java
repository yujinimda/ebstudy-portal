package com.ebstudy.portal.support;

import com.ebstudy.portal.admin.AdminAccountSeeder;
import com.ebstudy.portal.auth.ratelimit.CheckIdRateLimiter;
import com.ebstudy.portal.auth.ratelimit.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 통합 테스트 공통 기반 — test-strategy.md 5.1·5.2.
 *
 * <p><b>격리 방식은 "매번 초기화"다</b>(기본인 트랜잭션 롤백이 아니다). 이유:
 * 여기서는 실제 HTTP 로 요청하므로 테스트 스레드에 트랜잭션이 없고, 동시 가입 경합처럼
 * <b>실제 커밋 경합</b>을 보는 테스트가 있다. 5.2 가 요구한 "예외를 쓰는 테스트는 명시" 항목이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // 테스트에서 실제로 기다리지 않는다 — 대기는 무동작으로 갈아 끼운다(research.md 6)
        "auth.fail.delay-enabled=false",
        // 로컬 http 로 테스트하므로 Secure 를 끈다. 운영 기본값은 켠 쪽이다
        "auth.cookie.secure=false",
        // FR-022 시딩 검증용 값. 운영 값이 아니며 저장소에 남는 실제 비밀번호가 아니다
        "auth.seed.admin-username=" + IntegrationTestBase.ADMIN_USERNAME,
        "auth.seed.admin-password=" + IntegrationTestBase.ADMIN_PASSWORD,
        "auth.seed.admin-name=최고관리자"
})
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    public static final String ADMIN_USERNAME = "bossadmin";
    /** 테스트 고정값. 실제 운영 자격증명이 아니다(FR-023 은 운영 초기 비밀번호를 규정한다). */
    public static final String ADMIN_PASSWORD = "test-admin-pw-0001";
    public static final String VALID_PASSWORD = "Study1234abcd";

    @Autowired
    protected Environment environment;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected AdminAccountSeeder adminAccountSeeder;

    @Autowired
    protected LoginAttemptService loginAttempts;

    @Autowired
    protected CheckIdRateLimiter checkIdRateLimiter;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PostgresContainer.instance().getJdbcUrl());
        registry.add("spring.datasource.username", () -> PostgresContainer.instance().getUsername());
        registry.add("spring.datasource.password", () -> PostgresContainer.instance().getPassword());
    }

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("TRUNCATE TABLE refresh_tickets, users RESTART IDENTITY CASCADE");
        loginAttempts.clearAll();
        checkIdRateLimiter.clearAll();
        adminAccountSeeder.seedIfAbsent();
    }

    protected ApiClient newClient() {
        return new ApiClient("http://localhost:" + environment.getProperty("local.server.port"));
    }

    protected JsonNode json(String body) {
        return objectMapper.readTree(body);
    }

    protected ApiClient.Response signup(ApiClient client, String username, String password,
            String name) {
        return client.post("/api/auth/signup", """
                {"username":"%s","password":"%s","name":"%s"}
                """.formatted(username, password, name));
    }

    protected ApiClient.Response login(ApiClient client, String username, String password) {
        return client.post("/api/auth/login", """
                {"username":"%s","password":"%s"}
                """.formatted(username, password));
    }

    protected ApiClient.Response adminLogin(ApiClient client, String username, String password) {
        return client.post("/api/admin/auth/login", """
                {"username":"%s","password":"%s"}
                """.formatted(username, password));
    }
}
