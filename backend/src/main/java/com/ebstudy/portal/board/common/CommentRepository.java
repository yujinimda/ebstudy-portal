package com.ebstudy.portal.board.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 요구사항 4.2 — 상세 화면의 댓글 목록. 작성 순서대로. */
    List<Comment> findByPostIdOrderByCreatedAtAscIdAsc(Long postId);

    long countByPostId(Long postId);

    /**
     * ★ 목록·메인 화면의 <b>댓글 수</b>(요구사항 2장 · 4.1)를 한 번에 센다.
     *
     * <p>행마다 {@link #countByPostId(Long)} 를 부르면 한 페이지에 쿼리가 50번 나간다(N+1).
     * 목록에서는 반드시 이쪽을 쓴다.
     */
    @Query("select c.post.id as postId, count(c) as total from Comment c "
            + "where c.post.id in :postIds group by c.post.id")
    List<PostCommentCount> countGroupedByPostIds(@Param("postIds") List<Long> postIds);

    /** 댓글이 0개인 글은 결과에 없다 — 호출부에서 {@code getOrDefault(id, 0L)} 로 읽는다. */
    default Map<Long, Long> countsByPostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return countGroupedByPostIds(postIds).stream()
                .collect(Collectors.toMap(PostCommentCount::getPostId, PostCommentCount::getTotal,
                        (first, second) -> first, LinkedHashMap::new));
    }

    /** 프로젝션 인터페이스 — 엔티티를 그대로 내보내지 않는다. */
    interface PostCommentCount {
        Long getPostId();

        Long getTotal();
    }
}
