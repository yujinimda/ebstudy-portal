package com.ebstudy.portal.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 가입 규칙 숫자를 담는 상자 상수들 모아둔곳
 * 가입 입력 규칙의 경계값. <b>전부 환경변수로 바꿀 수 있다</b>(application.yml 의 {@code ${KEY}}).
 *
 * <p>기본값은 {@code spec.md} 의 FR/AC 값이다 — 아이디 4~11자(FR-003) · 비밀번호 8~64자(FR-006) ·
 * 이름 2~50자(FR-029). 원 기획서 값(비밀번호 4~11자 · 이름 2~4자)과 다르며,
 * 그 차이는 README 의 "스펙과 어긋나 보이는 지점"에 적었다.
 *
 * <p>{@code passwordMaxBytes} 는 <b>bcrypt 가 72바이트에서 입력을 자르기 때문에</b> 필요하다.
 * 조용히 잘리면 앞 72바이트가 같은 다른 비밀번호로 로그인이 성공한다 → {@code AC-2} 가 거짓이 된다.
 * 그래서 자르지 않고 <b>거부</b>한다.
 */
@ConfigurationProperties(prefix = "signup")
// record: 값만 담는 읽기 전용 객체
public record SignupPolicy(Bounds username, Bounds password, Bounds name, int passwordMaxBytes) {

    /** 둘 다 포함(inclusive). "12자 미만" 은 max=11 로 적는다. */
    public record Bounds(int min, int max) {
    }
}
