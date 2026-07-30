# ── 빌드 단계 ────────────────────────────────────────────────
# process.md 6.2 — 로컬도 Docker 로 띄워 이식성을 유지한다.
FROM eclipse-temurin:25.0.3_9-jdk AS build
WORKDIR /workspace

# 의존성 계층을 소스보다 먼저 만든다 — 소스만 바뀌면 의존성 다운로드를 다시 하지 않는다
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true

COPY src src
# 이미지 빌드에서는 테스트를 돌리지 않는다 — 통합 테스트가 Docker(Testcontainers)를 요구하므로
# Docker 안에서 Docker 를 띄우게 된다. 테스트는 ./gradlew test 로 호스트에서 돈다.
RUN ./gradlew --no-daemon bootJar -x test

# ── 실행 단계 ────────────────────────────────────────────────
FROM eclipse-temurin:25.0.3_9-jre
WORKDIR /app
RUN useradd --system --uid 10001 portal
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER portal
EXPOSE 8080
# 로그는 stdout 으로만 쓴다(process.md 4.9 · 12-Factor). 앱이 파일을 만들지 않는다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
