package com.ebstudy.portal.user;

import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 — FR-001~009 · AC-8~20.
 *
 * <p><b>모든 검증은 서버에서 한다</b>(FR-002). 화면 검증은 편의일 뿐이므로
 * 화면을 거치지 않은 직접 호출도 같은 코드로 거부된다({@code AC-19}).
 */
@Service
@RequiredArgsConstructor
public class SignupService {

    // static final 은 @RequiredArgsConstructor 가 건드리지 않는다 — 이미 값이 있으므로 주입 대상이 아니다
    private static final Pattern ALLOWED_USERNAME = Pattern.compile("^[A-Za-z0-9_-]+$");

    private final UserRepository users;
    private final ReservedUsernameRepository reserved;
    private final PasswordEncoder passwordEncoder;
    private final SignupPolicy policy;

    // Transactional 클레스 전체가 아니라 signup메서드에만 붙음
    // 이유는 조회만 하는곳은 트랜잭션이 필요가 없능니까 그런곳은 리드온리가 따로 붙는다
    @Transactional
    public User signup(String username, String password, String name) {
        String safeUsername = username == null ? "" : username;
        String safePassword = password == null ? "" : password;
        String safeName = name == null ? "" : name;

        validateUsername(safeUsername);
        validatePassword(safeUsername, safePassword);
        validateName(safeName);

        // 사전 확인은 사용자에게 정확한 코드를 주기 위한 것이고,
        // 실제 유일성 판정은 DB 유니크 인덱스가 한다(동시 가입 경합 — AC-10).
        if (users.existsByUsernameIgnoringCase(safeUsername)) {
            throw new ApiException(ErrorCode.USER_ID_DUPLICATED);
        }
        try {
            return users.saveAndFlush(User.create(safeUsername,
                    passwordEncoder.encode(safePassword), safeName, Role.USER,
                    OffsetDateTime.now()));
        } catch (DataIntegrityViolationException ex) {
            // DataIntegrityViolationException: db 에러 원문에는 테이블명, 인덱스 명이 들어있음 그대로 내보내기엔 내부 구조 유출이 되어서 우리가 만들어낸 깨끗한 에러로 교체
            // ★ AC-28 — 원본 메시지(테이블명·인덱스명 + 가입 여부)를 밖으로 내보내지 않는다.
            //   여기서 잡지 않으면 스프링 기본 동작이 그것을 그대로 흘린다.
            throw new ApiException(ErrorCode.USER_ID_DUPLICATED);
        }
    }

    /** AC-20 — 여기서 사용 불가로 답한 아이디는 가입에서도 거부된다. */
    @Transactional(readOnly = true)
    public boolean isAvailable(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        return !users.existsByUsernameIgnoringCase(username)
                && !reserved.existsIgnoringCase(username);
    }

    private void validateUsername(String username) {
        int length = charCount(username);
        if (length < policy.username().min() || length > policy.username().max()) {
            throw new ApiException(ErrorCode.USER_ID_LENGTH_INVALID);
        }
        if (!ALLOWED_USERNAME.matcher(username).matches()) {
            throw new ApiException(ErrorCode.USER_ID_FORMAT_INVALID);
        }
        if (reserved.existsIgnoringCase(username)) {
            throw new ApiException(ErrorCode.USER_ID_NOT_ALLOWED);
        }
    }

    private void validatePassword(String username, String password) {
        // 앞뒤 공백을 제거하지 않는다 — 비밀번호의 일부일 수 있다(spec.md Edge Cases).
        int length = charCount(password);
        if (length < policy.password().min() || length > policy.password().max()) {
            throw new ApiException(ErrorCode.USER_PASSWORD_LENGTH_INVALID);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > policy.passwordMaxBytes()) {
            // bcrypt 절단 방지. 조용히 자르면 틀린 비밀번호로 로그인이 성공한다.
            throw new ApiException(ErrorCode.USER_PASSWORD_LENGTH_INVALID);
        }
        if (!username.isEmpty()
                && password.toLowerCase().contains(username.toLowerCase())) {
            throw new ApiException(ErrorCode.USER_PASSWORD_CONTAINS_ID);
        }
        if (hasThreeRepeatedChars(password)) {
            throw new ApiException(ErrorCode.USER_PASSWORD_REPEATED_CHAR);
        }
    }

    private void validateName(String name) {
        int length = charCount(name);
        if (length < policy.name().min() || length > policy.name().max()) {
            throw new ApiException(ErrorCode.USER_NAME_LENGTH_INVALID);
        }
    }

    /** 길이는 <b>문자 수</b>로 센다 — 한글·이모지 허용(spec.md Edge Cases). */
    static int charCount(String value) {
        return value.codePointCount(0, value.length());
    }

    static boolean hasThreeRepeatedChars(String value) {
        int run = 1;
        int previous = -1;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == previous) {
                run++;
                if (run >= 3) {
                    return true;
                }
            } else {
                run = 1;
                previous = cp;
            }
        }
        return false;
    }
}
