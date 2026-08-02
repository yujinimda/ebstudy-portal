package com.ebstudy.portal.admin;

import com.ebstudy.portal.auth.AuthController;
import com.ebstudy.portal.auth.AuthCookies;
import com.ebstudy.portal.auth.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4. {@code POST /api/admin/auth/login} — <b>별도 진입점</b>(FR-032).
 *
 * <p>인증 <b>메커니즘은 공유</b>하고 진입점과 정책만 분리한다 — 두 벌 만들면 취약점 표면이
 * 두 배이고 한쪽을 고칠 때 다른 쪽을 잊는다(FR-017).
 *
 * <p>★ {@code AC-32}: {@code USER} 계정이 <b>정확한 비밀번호로</b> 시도해도 실패시키되
 * 응답은 {@code AC-2}·{@code AC-3} 과 <b>완전히 같다</b>.
 * {@code "관리자 권한이 없습니다"} 로 답하면 그 아이디가 존재하고 일반 사용자라는 것을 알려준다.
 * 403 이 아니라 401 인 것도 같은 이유다.
 */
@RestController
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;
    private final AuthCookies cookies;

    @PostMapping("/api/admin/auth/login")
    public AuthController.UserResponse login(@RequestBody AuthController.LoginRequest request,
            HttpServletResponse response) {
        // adminEntryPoint=true — 권한 판정은 AuthService 안에서 비밀번호 검증을 끝낸 뒤에 일어난다
        AuthService.Session session = authService.login(request.username(), request.password(), true);
        cookies.write(response,
                cookies.access(session.accessToken(), session.accessTtl()),
                cookies.refresh(session.refreshToken(), session.refreshTtl()));
        return AuthController.UserResponse.of(session.user());
    }
}
