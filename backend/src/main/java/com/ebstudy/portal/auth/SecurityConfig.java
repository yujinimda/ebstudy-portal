package com.ebstudy.portal.auth;

import com.ebstudy.portal.common.ProblemAuthEntryPoints;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// 경비실 규칙표. "어떤 URL은 통과, 어떤 URL은 로그인 필수" 설정
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * ADR-005 는 Argon2id 를 정했으나 <b>이 구현은 bcrypt 를 쓴다</b>(사용자 지시).
     * bcrypt 의 72바이트 절단 위험은 <b>72바이트 초과 입력을 거부</b>하는 것으로 막았다
     * ({@code SignupService} · {@code AuthService}) — 조용히 자르면 틀린 비밀번호로 로그인이
     * 성공하는 입력이 생겨 {@code AC-2} 가 거짓이 된다. 자세한 어긋남은 README 에 적었다.
     */
    @Bean
    public PasswordEncoder passwordEncoder(
            @Value("${auth.password.bcrypt-strength:10}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthCookies cookies,
            JwtIssuer jwtIssuer, ProblemAuthEntryPoints problemEntryPoints) throws Exception {
        return http
                // CSRF 1차 방어선은 SameSite=Lax 쿠키다(contracts/auth-api.md).
                // 방아쇠: GET 으로 상태를 바꾸는 엔드포인트가 생기거나 크로스 오리진이 필요해지는 시점
                // — 그때 CSRF 토큰을 도입한다.
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 인증 없이 열려 있는 경로. /api/admin/auth/login 이 /api/admin/** 보다 먼저 온다
                        .requestMatchers("/api/auth/signup", "/api/auth/check-id",
                                "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                                "/api/admin/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // FR-018 · AC-21·22·23 — 관리자 전용은 서버가 막는다.
                        // 아래 게시판 규칙보다 먼저 와야 한다 — 순서가 곧 우선순위다
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 002 요구사항 1.3 — 목록·상세 조회는 <b>누구나</b> 한다.
                        // 읽기(GET)만 연다. 등록·수정·삭제는 아래 anyRequest 가 그대로 막는다
                        .requestMatchers(HttpMethod.GET,
                                "/api/categories/**", "/api/notices/**", "/api/free-posts/**",
                                "/api/galleries/**", "/api/inquiries/**").permitAll()
                        // 요구사항 6.2 — 비밀글 잠금해제는 <b>로그인이 필요 없다</b>.
                        // 비밀번호를 아는 사람이 여는 경로이지 로그인한 사람이 여는 경로가 아니다
                        // (남용은 SecretPasswordAttemptService 의 글·클라이언트 단위 임계가 막는다)
                        .requestMatchers(HttpMethod.POST, "/api/inquiries/*/unlock").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtCookieAuthenticationFilter(cookies, jwtIssuer),
                        UsernamePasswordAuthenticationFilter.class)
                // 어댑터 2/3 · 3/3 — 공통 Problem Details 생성기를 공유한다(research.md 15)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problemEntryPoints.entryPoint())
                        .accessDeniedHandler(problemEntryPoints.accessDeniedHandler()))
                .build();
    }
}
