package com.ebstudy.portal.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 쿠키를 직접 다루는 HTTP 클라이언트.
 *
 * <p>브라우저 대신 <b>Set-Cookie 헤더를 눈으로 확인</b>해야 하므로 자동 쿠키 처리에 맡기지 않는다
 * ({@code AC-1} 은 쿠키 속성 자체가 검증 대상이고, {@code AC-2}/{@code AC-3}/{@code AC-32} 는
 * "셋 다 Set-Cookie 를 보내지 않는다"가 검증 대상이다).
 *
 * <p>클라이언트 인스턴스 하나 = 브라우저 하나. {@code AC-35}(로그아웃은 요청한 기기만)를
 * 검증하려면 인스턴스를 2개 만든다.
 */
public class ApiClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String baseUrl;
    private final Map<String, String> cookieJar = new LinkedHashMap<>();

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Response post(String path, String json) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)));
    }

    public Response get(String path) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
    }

    public Response getWithHeader(String path, String name, String value) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).header(name, value).GET());
    }

    private Response send(HttpRequest.Builder builder) {
        if (!cookieJar.isEmpty()) {
            String header = cookieJar.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
            builder.header("Cookie", header);
        }
        try {
            HttpResponse<String> response = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            List<String> setCookies = response.headers().allValues("set-cookie");
            applyCookies(setCookies);
            return new Response(response.statusCode(), response.headers().map(), response.body(),
                    setCookies);
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("요청 실패", ex);
        }
    }

    private void applyCookies(List<String> setCookies) {
        for (String setCookie : setCookies) {
            String pair = setCookie.split(";", 2)[0];
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String name = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            boolean deleted = setCookie.toLowerCase().contains("max-age=0") || value.isEmpty();
            if (deleted) {
                cookieJar.remove(name);
            } else {
                cookieJar.put(name, value);
            }
        }
    }

    public Optional<String> cookie(String name) {
        return Optional.ofNullable(cookieJar.get(name));
    }

    public void putCookie(String name, String value) {
        cookieJar.put(name, value);
    }

    public void clearCookies() {
        cookieJar.clear();
    }

    public record Response(int status, Map<String, List<String>> headers, String body,
            List<String> setCookies) {

        public Optional<String> header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream())
                    .findFirst();
        }

        public Optional<String> setCookie(String name) {
            return setCookies.stream().filter(c -> c.startsWith(name + "=")).findFirst();
        }
    }
}
