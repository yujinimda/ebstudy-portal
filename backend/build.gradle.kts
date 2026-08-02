// 스택: Java 25 LTS · Spring Boot 4.1 · Gradle Kotlin DSL  (process.md 6.1)
plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.ebstudy.portal"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        // process.md 6.1 — Java 25 LTS
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // JWT — Spring Security 내장(Nimbus). 별도 JWT 라이브러리를 추가하지 않는다 (research.md 10)
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // 마이그레이션 — Flyway. 앱은 DDL을 만들지 않는다 (process.md 4.7)
    // ⚠️ Spring Boot 4 는 자동설정이 모듈로 쪼개졌다 — spring-boot-flyway 가 없으면
    //    flyway-core 만 있어도 마이그레이션이 조용히 실행되지 않는다(실제로 겪었다).
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.postgresql:postgresql")

    // 반복 코드 생성 — getter·생성자·logger. 컴파일 시점에만 필요하므로 compileOnly 다
    // (런타임 산출물에 lombok.jar 가 들어가지 않는다).
    // ⚠️ JDK 25 지원은 1.18.40 부터다. 그보다 낮은 버전으로 내려가면 컴파일이 깨진다.
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // 통합 테스트는 실제 PostgreSQL 에 붙는다 (test-strategy.md 5.1)
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
