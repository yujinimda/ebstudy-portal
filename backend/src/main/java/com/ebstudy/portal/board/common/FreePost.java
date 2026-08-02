package com.ebstudy.portal.board.common;

import com.ebstudy.portal.user.User;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 자유게시판(002) 글 — V11 {@code free_posts}.
 *
 * <p><b>전용 칸이 없다.</b> 그래도 표를 두는 이유는 {@code JOINED} 상속이
 * 구상 클래스마다 표를 하나씩 요구하기 때문이다. 대가는 조회 때 조인 하나이고,
 * 얻는 것은 <i>"게시판마다 자기 표를 가진다"</i> 는 규칙이 예외 없이 성립하는 것이다.
 *
 * <p>전용 칸이 생기면 여기에 붙인다 — 그때 {@code posts} 를 건드리지 않아도 된다는 것이
 * 이 구조의 값이다.
 */
@Entity
@Table(name = "free_posts")
@DiscriminatorValue("FREE")
@PrimaryKeyJoinColumn(name = "post_id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FreePost extends Post {

    FreePost(Category category, User author, String title, String content, OffsetDateTime now) {
        super(category, author, title, content, now);
    }
}
