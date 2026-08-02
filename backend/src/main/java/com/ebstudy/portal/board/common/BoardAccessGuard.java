package com.ebstudy.portal.board.common;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import com.ebstudy.portal.user.Role;
import org.springframework.stereotype.Component;

/**
 * 권한 검증 — 요구사항 1.3.
 *
 * <p>★ <i>"버튼도 본인에게만 보이되 <b>서버가 검증한다</b>"</i> (001 {@code FR-019} · {@code AC-26}).
 * 화면에서 버튼을 숨기는 것은 권한 검증이 아니다. 이 클래스를 거치지 않은 수정·삭제 경로는
 * 곧 <b>남의 글을 지울 수 있는 경로</b>다.
 *
 * <p>한 곳에 모은 이유: 같은 판정을 게시판 4종 × (글·댓글·답변) 마다 쓰면 12벌이 되고,
 * 그중 하나만 빠뜨려도 그 하나가 구멍이다.
 */
@Component
public class BoardAccessGuard {

    /** 비로그인이면 401 — 요구사항 1.3 "미로그인 상태로 글 등록 시도 → 로그인 화면으로". */
    public AuthenticatedUser requireLogin(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED);
        }
        return principal;
    }

    public boolean isAdmin(AuthenticatedUser principal) {
        return principal != null && principal.role() == Role.ADMIN;
    }

    public boolean isOwner(AuthenticatedUser principal, Long ownerId) {
        return principal != null && principal.userId() != null && ownerId != null
                && principal.userId().equals(ownerId);
    }

    /** 관리자 전용 동작(공지 등록 · 문의 답변 · 카테고리 관리). */
    public void requireAdmin(AuthenticatedUser principal) {
        requireLogin(principal);
        if (!isAdmin(principal)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    /** 요구사항 0장 "글 등록 주체" — 공지사항은 관리자만, 나머지는 로그인 사용자. */
    public void requireCanWrite(BoardType boardType, AuthenticatedUser principal) {
        if (boardType.adminOnlyWrite()) {
            requireAdmin(principal);
            return;
        }
        requireLogin(principal);
    }

    /**
     * 요구사항 1.3 "글 수정·삭제는 <b>본인 글만</b>".
     *
     * <p>관리자에게도 열지 않는다 — 요구사항이 관리자 예외를 <b>댓글 삭제에만</b> 줬다.
     * 관리자의 글 삭제는 별도 관리 화면(요구사항 3.3 · 6.5)의 동작이고 그쪽은
     * {@link #requireAdmin} 을 쓴다. 둘을 섞으면 사용자 화면에서 관리자가 남의 글을
     * 고칠 수 있게 되는데, 그건 요구사항에 없는 권한이다.
     */
    public void requireOwner(AuthenticatedUser principal, Long ownerId) {
        requireLogin(principal);
        if (!isOwner(principal, ownerId)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    /** 요구사항 1.3 "댓글 삭제는 본인 댓글만. <b>관리자는 모든 댓글 삭제 가능</b>". */
    public void requireOwnerOrAdmin(AuthenticatedUser principal, Long ownerId) {
        requireLogin(principal);
        if (!isOwner(principal, ownerId) && !isAdmin(principal)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    /**
     * 요구사항 6.2 — 비밀글 상세.
     * <b>본인이 작성한 글이면 비밀번호 입력을 건너뛴다.</b> 관리자도 건너뛴다
     * (요구사항 6.5 가 관리자에게 답변 의무를 줬으므로 열지 못하면 답변할 수 없다).
     *
     * @return {@code true} 면 비밀번호 확인 없이 열어도 된다
     */
    public boolean canSkipSecretPassword(AuthenticatedUser principal, Long authorId) {
        return isOwner(principal, authorId) || isAdmin(principal);
    }
}
