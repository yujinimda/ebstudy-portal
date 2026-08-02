package com.ebstudy.portal.board.free;

import com.ebstudy.portal.board.common.Category;
import com.ebstudy.portal.board.common.Post;
import java.time.OffsetDateTime;

/**
 * 목록 한 줄 — 요구사항 4.1
 * <i>"번호 · 분류 · 제목(댓글 수 · new · 첨부 아이콘) · 조회 · 등록일시 · 등록자"</i>.
 *
 * @param number        요구사항 1.1 "전체 게시글 수 기준 역순". <b>고정 식별자가 아니다</b> —
 *                      상세로 가는 링크는 반드시 {@code id} 를 쓴다({@code PostNumbering} 주석)
 * @param newBadge      {@code new} 아이콘 여부. 서버가 판정한다 — 화면에서 계산하면
 *                      사용자 기기의 시계가 기준이 된다({@code NewBadgePolicy})
 * @param hasAttachment 요구사항 4.1 "첨부 아이콘". 개수까지 주는 것은 화면이 {@code +3} 같은
 *                      표기를 하고 싶어질 때 요청을 다시 만들지 않게 하기 위해서다
 */
public record FreePostListItem(
        long number,
        Long id,
        Long categoryId,
        String categoryName,
        String title,
        long commentCount,
        long attachmentCount,
        boolean hasAttachment,
        boolean newBadge,
        long viewCount,
        String authorName,
        OffsetDateTime createdAt) {

    static FreePostListItem of(Post post, long number, long commentCount, long attachmentCount,
            boolean newBadge) {
        Category category = post.getCategory();
        return new FreePostListItem(
                number,
                post.getId(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                post.getTitle(),
                commentCount,
                attachmentCount,
                attachmentCount > 0,
                newBadge,
                post.getViewCount(),
                // 탈퇴·삭제된 사용자는 V6 의 FK RESTRICT 때문에 생기지 않지만,
                // 목록 한 줄 때문에 500 을 내지는 않는다
                post.getAuthor() == null ? null : post.getAuthor().getName(),
                post.getCreatedAt());
    }
}
