package com.ebstudy.portal.auth;

import com.ebstudy.portal.user.Role;

/** Access 토큰에서 꺼낸 주체. DB 를 보지 않는다(ADR-001 무상태 Access). */
public record AuthenticatedUser(Long userId, String username, Role role) {
}
