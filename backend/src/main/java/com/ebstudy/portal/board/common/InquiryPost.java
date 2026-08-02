package com.ebstudy.portal.board.common;

import com.ebstudy.portal.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 문의 글 — V11 {@code inquiry_posts}.
 *
 * <p>문의에만 있는 칸은 <b>비밀글 여부와 그 비밀번호 해시</b>다. 예전에는 {@code posts} 에
 * 있어서 <i>"공지 글에 비밀번호 해시가 들어가는"</i> 상태를 CHECK 제약으로 막아야 했다.
 * 지금은 문의 표에만 있으므로 그런 행이 만들어질 수 없다.
 *
 * <p>두 칸의 짝 규칙(비밀글이면 해시가 있어야 하고, 아니면 없어야 한다)은 남는다 —
 * 그건 게시판 구분이 아니라 <b>이 표 안의 규칙</b>이라
 * V11 {@code ck_inquiry_posts_secret_password} 가 지킨다.
 */
@Entity
@Table(name = "inquiry_posts")
@DiscriminatorValue("INQUIRY")
@PrimaryKeyJoinColumn(name = "post_id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryPost extends Post {

    @Column(name = "secret", nullable = false)
    private boolean secret;

    /**
     * ⚠️ 4자리 비밀번호의 <b>해시</b>다. 원문이 아니다.
     * DTO 로 내보내지 않는다 — 엔티티를 그대로 응답하지 않는 규약이 1차 방어선이고,
     * 검증은 {@code SecretPostPassword} 하나를 통해서만 한다.
     */
    @Column(name = "secret_password_hash", length = 255)
    private String secretPasswordHash;

    InquiryPost(User author, String title, String content, String secretPasswordHash,
            OffsetDateTime now) {
        // 문의게시판은 분류가 없다(요구사항 6.1)
        super(null, author, title, content, now);
        this.secretPasswordHash = secretPasswordHash;
        this.secret = secretPasswordHash != null;
    }

    boolean secret() {
        return secret;
    }

    String secretPasswordHash() {
        return secretPasswordHash;
    }

    /** {@link Post#changeSecret} 를 통해서만 부른다 — 두 칸을 따로 바꿀 길을 만들지 않는다. */
    void applySecret(String secretPasswordHash, OffsetDateTime now) {
        this.secretPasswordHash = secretPasswordHash;
        this.secret = secretPasswordHash != null;
        touch(now);
    }
}
