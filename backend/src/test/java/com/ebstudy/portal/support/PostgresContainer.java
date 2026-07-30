package com.ebstudy.portal.support;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * test-strategy.md 5.1 — <b>실제 PostgreSQL</b>에 붙는다. 임베디드 DB 를 쓰면
 * "실제 DB 와 붙는 경계를 본다"는 전략의 근거가 무너지고, 손으로 쓴 마이그레이션 SQL 이
 * 실제로 도는지 확인할 방법이 없어진다.
 *
 * <p><b>이미지 태그는 docker-compose.yml 과 같은 값으로 고정</b>한다(ADR-003 리스크 2).
 */
public final class PostgresContainer {

    public static final String IMAGE = "postgres:17.10";

    private static final PostgreSQLContainer INSTANCE = new PostgreSQLContainer(IMAGE)
            .withDatabaseName("portal")
            .withUsername("portal")
            .withPassword("portal");

    static {
        INSTANCE.start();
    }

    private PostgresContainer() {
    }

    public static PostgreSQLContainer instance() {
        return INSTANCE;
    }
}
