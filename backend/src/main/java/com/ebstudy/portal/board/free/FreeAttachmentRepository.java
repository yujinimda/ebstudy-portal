package com.ebstudy.portal.board.free;

import com.ebstudy.portal.board.common.Attachment;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 목록의 <b>첨부 아이콘</b>(요구사항 4.1)을 위한 집계.
 *
 * <p>공통 {@code AttachmentRepository.findByPostIdInOrder...} 로도 구할 수 있지만 그것은
 * 첨부 <b>행 전체</b>를 끌어온다 — 목록에 필요한 것은 "있느냐/몇 개냐" 뿐이라
 * 한 페이지에 최대 50 × 5 = 250행을 엔티티로 되살릴 이유가 없다.
 * 여기서는 {@code COUNT} 를 <b>DB 가 세게</b> 하고 숫자만 받는다.
 *
 * <p>행마다 {@code countByPostId} 를 부르면 한 페이지에 쿼리 50번(N+1)이다. 목록에서는
 * 반드시 이쪽을 쓴다 — 댓글 수를 {@code CommentRepository.countsByPostIds} 로 구하는 것과 같은 이유다.
 */
public interface FreeAttachmentRepository extends JpaRepository<Attachment, Long> {

    @Query("select a.post.id as postId, count(a) as total from Attachment a "
            + "where a.post.id in :postIds group by a.post.id")
    List<PostAttachmentCount> countGroupedByPostIds(@Param("postIds") List<Long> postIds);

    /** 첨부가 0개인 글은 결과에 없다 — 호출부에서 {@code getOrDefault(id, 0L)} 로 읽는다. */
    default Map<Long, Long> countsByPostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            // in () 이 빈 목록이면 DB 마다 동작이 다르다. 아예 쿼리를 내지 않는다
            return Map.of();
        }
        return countGroupedByPostIds(postIds).stream()
                .collect(Collectors.toMap(PostAttachmentCount::getPostId,
                        PostAttachmentCount::getTotal, (first, second) -> first,
                        LinkedHashMap::new));
    }

    /** 프로젝션 — 엔티티를 그대로 내보내지 않는다. */
    interface PostAttachmentCount {
        Long getPostId();

        Long getTotal();
    }
}
