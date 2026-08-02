package com.ebstudy.portal.board.free;

import com.ebstudy.portal.board.common.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 자유게시판 댓글 조회.
 *
 * <p>공통 {@code CommentRepository} 에도 같은 이름의 조회가 있지만 그쪽은 그래프가 없다.
 * 상세 화면은 댓글마다 <b>작성자 이름</b>을 찍으므로(요구사항 4.2) 그대로 쓰면
 * 댓글 30개에 쿼리 31번이 나간다 → 여기서 {@code author} 를 함께 읽는다.
 *
 * <p>{@code post} 는 일부러 페치하지 않는다 — 이미 상세에서 읽은 글이고,
 * 소유자 판정에 필요한 것은 <b>id 뿐</b>이라 프록시에서 초기화 없이 꺼낼 수 있다.
 */
public interface FreeCommentRepository extends JpaRepository<Comment, Long> {

    /** 요구사항 4.2 — 작성 순서대로. */
    @EntityGraph(attributePaths = "author")
    List<Comment> findWithAuthorByPostIdOrderByCreatedAtAscIdAsc(Long postId);
}
