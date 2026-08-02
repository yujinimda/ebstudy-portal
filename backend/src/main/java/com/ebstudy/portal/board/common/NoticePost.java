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
 * 공지사항 글 — V11 {@code notice_posts}.
 *
 * <p>공지에만 있는 칸은 <b>상단 고정</b> 하나다. 예전에는 {@code posts.pinned} 에 있었고
 * 자유·갤러리·문의 행에서도 늘 {@code false} 로 자리를 차지했다.
 * 지금은 <b>공지 표에만 있으므로 다른 게시판이 이 값을 가질 방법이 없다</b> —
 * V6 의 {@code ck_posts_pinned} 제약이 하던 일을 스키마 구조가 대신한다.
 */
@Entity
@Table(name = "notice_posts")
@DiscriminatorValue("NOTICE")
@PrimaryKeyJoinColumn(name = "post_id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticePost extends Post {

    /** 요구사항 3.1 알림글. 노출 상한(최대 5개)은 조회 쪽이 지킨다 — 여기는 켜짐/꺼짐만 안다. */
    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    NoticePost(Category category, User author, String title, String content, boolean pinned,
            OffsetDateTime now) {
        super(category, author, title, content, now);
        this.pinned = pinned;
    }

    boolean pinned() {
        return pinned;
    }

    /** {@link Post#changePinned} 를 통해서만 부른다 — 게시판 판정이 거기 있다. */
    void applyPinned(boolean pinned, OffsetDateTime now) {
        this.pinned = pinned;
        touch(now);
    }
}
