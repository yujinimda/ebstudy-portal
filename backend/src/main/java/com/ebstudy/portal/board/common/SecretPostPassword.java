package com.ebstudy.portal.board.common;

import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 비밀글 잠금 비밀번호 — 요구사항 6.2 <i>"잠금 비밀번호 4자리"</i>.
 *
 * <p>★ <b>평문으로 저장하지 않는다.</b> 4자리 숫자는 1만 가지라 해시로도 전수 대입이 순식간이다.
 * 그럼에도 해시로 두는 이유는 <b>유출 범위</b>다 — 사용자는 다른 서비스와 같은 4자리를 쓴다.
 * 원문을 남기면 우리 DB 유출이 그 사람의 다른 계정 피해로 번진다.
 *
 * <p>전수 대입 자체는 해시로 막지 못한다. 그래서 짝이 되는 방어가 따로 필요하다:
 * <b>시도 횟수 제한</b>(001 의 {@code LoginAttemptService} 와 같은 성격).
 * ▶ 이 단계에서는 만들지 않았다. 보고서의 후속 항목이다.
 *
 * <p>bcrypt 를 쓰는 것은 001 {@code SecurityConfig} 의 {@code PasswordEncoder} 빈을 그대로
 * 재사용하기 위해서다 — 해싱 알고리즘이 프로젝트에 둘이 되지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class SecretPostPassword {

    /** 요구사항 6.2 "4자리". 숫자 4자로 못 박는다 — 화면 입력이 숫자 키패드다. */
    private static final Pattern FOUR_DIGITS = Pattern.compile("^[0-9]{4}$");

    private final PasswordEncoder passwordEncoder;

    /** 등록·수정 시 원문 → 해시. 원문은 여기서만 보이고 밖으로 나가지 않는다. */
    public String hash(String raw) {
        if (raw == null || !FOUR_DIGITS.matcher(raw).matches()) {
            throw new ApiException(ErrorCode.SECRET_PASSWORD_FORMAT_INVALID);
        }
        return passwordEncoder.encode(raw);
    }

    public boolean matches(String raw, String storedHash) {
        if (raw == null || storedHash == null) {
            return false;
        }
        return passwordEncoder.matches(raw, storedHash);
    }

    /**
     * 상세 진입 시 확인. 틀리면 403 이다.
     *
     * <p>"비밀글이 아닌데 확인을 요구" 하는 호출도 거부한다 — 그 상태로 통과시키면
     * 호출부가 비밀글 여부를 안 보고 열어도 되는 것처럼 보이게 된다.
     */
    public void verify(String raw, String storedHash) {
        if (storedHash == null || !matches(raw, storedHash)) {
            // 메시지는 001 AUTH_INVALID_CREDENTIALS 처럼 "무엇이 틀렸는지" 를 나누지 않는다
            throw new ApiException(ErrorCode.SECRET_PASSWORD_MISMATCH);
        }
    }
}
