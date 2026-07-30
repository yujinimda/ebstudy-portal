package com.ebstudy.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// UserDetailsServiceAutoConfiguration 을 뺀다 — 우리는 폼 로그인·Basic 인증을 쓰지 않고
// 인증은 쿠키의 JWT 로만 한다. 켜 두면 쓰지 않는 인메모리 사용자와 생성된 비밀번호가
// 부팅 로그에 찍힌다(원칙 V — 로그에 자격증명 성격의 값을 남기지 않는다).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class PortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }
}
