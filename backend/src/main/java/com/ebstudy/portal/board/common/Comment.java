package com.ebstudy.portal.board.common;

import com.ebstudy.portal.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 댓글 — V7 {@code comments}. 요구사항 4.2(자유게시판).
 *
 * <p>자유게시판 전용이지만 {@code board.common} 에 둔다: 참조 대상이 공통 {@link Post} 이고,
 * 메인 페이지(요구사항 2장)가 자유게시판 글의 <b>댓글 수</b>를 쓴다.
 *
 * <p>수정 메서드가 없는 것은 의도다 — 요구사항 4.2 에 댓글 수정이 없다.
 * 있는 것만 만든다.
 */
@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    /** ⚠️ 1000자는 기획에 없어 정한 값이다(V7 주석 참조 — 사람 검증 대상). */
    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private Comment(Post post, User author, String content, OffsetDateTime now) {
        this.post = post;
        this.author = author;
        this.content = content;
        this.createdAt = now;
    }

    public static Comment create(Post post, User author, String content, OffsetDateTime now) {
        return new Comment(post, author, content, now);
    }

    /** 요구사항 1.3 — 본인 댓글만 삭제. 관리자는 예외이며 그 판정은 {@code BoardAccessGuard} 가 한다. */
    public boolean isOwnedBy(Long userId) {
        return userId != null && author != null && userId.equals(author.getId());
    }
}
