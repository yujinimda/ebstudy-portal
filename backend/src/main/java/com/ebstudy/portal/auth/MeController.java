package com.ebstudy.portal.auth;

import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import com.ebstudy.portal.user.User;
import com.ebstudy.portal.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 7. {@code GET /api/me} — 인증 필요. 계약 7번.
 * 스텁이 아니다: 화면 헤더의 로그인 정보로 계속 쓰이고 {@code AC-4} 검증 대상이다(research.md 12).
 */
@RestController
@RequiredArgsConstructor
public class MeController {

    private final UserRepository users;

    @GetMapping("/api/me")
    public AuthController.UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = users.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED));
        return AuthController.UserResponse.of(user);
    }
}
