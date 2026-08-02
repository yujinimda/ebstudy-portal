package com.ebstudy.portal.admin;

import com.ebstudy.portal.auth.AuthController;
import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import com.ebstudy.portal.user.User;
import com.ebstudy.portal.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 8. {@code GET /api/admin/me} — 관리자 전용. {@code AC-21}(401) · {@code AC-22}·{@code AC-26}(403) ·
 * {@code AC-23}(200) 의 대상이다.
 *
 * <p>권한 판정은 {@code SecurityConfig} 가 한다 — 화면에서 버튼을 숨기는 것은 권한 검증이 아니다(FR-019).
 */
@RestController
@RequiredArgsConstructor
public class AdminMeController {

    private final UserRepository users;

    @GetMapping("/api/admin/me")
    public AuthController.UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = users.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED));
        return AuthController.UserResponse.of(user);
    }
}
