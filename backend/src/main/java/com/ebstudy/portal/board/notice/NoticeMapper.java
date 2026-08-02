package com.ebstudy.portal.board.notice;

import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.Category;
import com.ebstudy.portal.board.common.NewBadgePolicy;
import com.ebstudy.portal.board.common.Post;
import com.ebstudy.portal.board.common.PostNumbering;
import com.ebstudy.portal.user.User;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 엔티티 → 응답 DTO.
 *
 * <p>사용자 목록·관리 목록·상세가 같은 매핑을 쓰게 모아 둔 것이다. 셋이 각자 매핑하면
 * <b>같은 글의 분류명이 화면마다 다르게 나오는</b> 종류의 어긋남이 생긴다.
 *
 * <p>★ 이 클래스의 모든 메서드는 <b>서비스 트랜잭션 안에서만</b> 불러야 한다.
 * {@code spring.jpa.open-in-view=false} 이므로 밖에서 부르면 LAZY 연관에서 터진다.
 */
@Component
@RequiredArgsConstructor
class NoticeMapper {

    private final NewBadgePolicy newBadgePolicy;

    /**
     * 목록 한 페이지 분량.
     *
     * @param totalElements 번호 계산의 기준 — 요구사항 1.1 "전체 게시글 수 기준 역순".
     *                      <b>이 구현은 "현재 검색 조건의 전체 결과 수"를 넣는다.</b>
     *                      게시판 전체 수를 넣으면 검색 결과 3건의 첫 줄에 137 이 찍히고
     *                      마지막 줄이 1 이 아니게 되어, 페이징 표시와 번호가 서로 다른 세계를
     *                      가리킨다. 게시판 4종이 같은 쪽으로 통일해야 하는 값이다(보고서 참조)
     * @param numbered      {@code false} 면 번호를 주지 않는다 — 요구사항 3.1 의 고정 글이 그 경우다
     */
    List<NoticeListItem> toListItems(List<Post> posts, long totalElements, int page, int size,
            boolean numbered, OffsetDateTime now) {
        long[] numbers = numbered
                ? PostNumbering.displayNumbers(totalElements, page, size, posts.size())
                : null;
        List<NoticeListItem> items = new ArrayList<>(posts.size());
        for (int i = 0; i < posts.size(); i++) {
            items.add(toListItem(posts.get(i), numbers == null ? null : numbers[i], now));
        }
        return items;
    }

    NoticeListItem toListItem(Post post, Long displayNumber, OffsetDateTime now) {
        Category category = post.getCategory();
        User author = post.getAuthor();
        return new NoticeListItem(
                post.getId(),
                displayNumber,
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                post.getTitle(),
                post.getViewCount(),
                post.getCreatedAt(),
                author == null ? null : author.getName(),
                post.isPinned(),
                newBadgePolicy.isNew(BoardType.NOTICE, post.getCreatedAt(), now));
    }

    /**
     * @param viewCount 조회수를 인자로 받는 이유: 상세 조회는 조회수를 <b>DB 에서</b> 올리는데
     *                  (동시 요청이 묻히지 않게) 그러면 방금 읽은 엔티티의 값은 증가 전이다.
     *                  화면에 증가 전 값을 주면 새로고침해야 자기 조회가 반영되는 것처럼 보인다
     * @param editable  요구사항 1.3 — 버튼 표시용 힌트일 뿐이다. 실제 검증은 관리 API 가 다시 한다
     */
    NoticeDetailResponse toDetail(Post post, long viewCount, boolean editable) {
        Category category = post.getCategory();
        User author = post.getAuthor();
        return new NoticeDetailResponse(
                post.getId(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                post.getTitle(),
                post.getContent(),
                viewCount,
                post.getCreatedAt(),
                post.getUpdatedAt(),
                author == null ? null : author.getName(),
                post.isPinned(),
                editable);
    }
}
