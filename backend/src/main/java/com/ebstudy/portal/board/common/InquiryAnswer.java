package com.ebstudy.portal.board.common;

import com.ebstudy.portal.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문의 답변 — V9 {@code inquiry_answers}. 요구사항 6.3 · 6.5.
 *
 * <p>"답변완료" 는 이 행의 <b>존재 여부</b>로 판정한다. {@link Post} 에 플래그를 두지 않는다 —
 * 같은 사실이 두 곳에 있으면 반드시 어긋난다(V9 헤더).
 */
@Entity
@Table(name = "inquiry_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 문의글 하나에 답변 하나. 유일성은 V9 {@code uk_inquiry_answers_post_id} 가 지킨다. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    /** 요구사항 6.5 "필수 4000자 미만" → 3999. */
    @Column(name = "content", nullable = false, length = 3999)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private InquiryAnswer(Post post, User admin, String content, OffsetDateTime now) {
        this.post = post;
        this.admin = admin;
        this.content = content;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static InquiryAnswer create(Post post, User admin, String content, OffsetDateTime now) {
        return new InquiryAnswer(post, admin, content, now);
    }

    public void update(User admin, String content, OffsetDateTime now) {
        this.admin = admin;
        this.content = content;
        this.updatedAt = now;
    }
}
