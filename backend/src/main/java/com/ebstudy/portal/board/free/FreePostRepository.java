package com.ebstudy.portal.board.free;

import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.Post;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 자유게시판 전용 게시글 조회.
 *
 * <p>공통 {@code PostRepository} 를 고치지 않고 여기 따로 만든 이유는 파일 경계(요구사항 9장)
 * 때문만이 아니다. <b>필요한 페치 전략이 게시판마다 다르다.</b> 같은 엔티티에 리포지토리가
 * 여러 개 있는 것은 정상이다.
 *
 * <p>★ <b>N+1 방어가 이 인터페이스의 존재 이유다.</b>
 * {@code Post.author} 와 {@code Post.category} 는 {@code LAZY} 다. 목록 50건을 DTO 로 옮기면서
 * {@code getAuthor().getName()} · {@code getCategory().getName()} 을 읽으면
 * <b>쿼리가 1 + 50 + 50 = 101번</b> 나간다. {@link EntityGraph} 로 두 연관을 목록 쿼리에
 * 함께 실어 <b>1번</b>으로 만든다.
 *
 * <p>연관이 둘 다 {@code @ManyToOne} 이라 조인해도 행이 늘지 않는다 —
 * 컬렉션을 페치 조인했다면 페이징이 메모리에서 일어나 같은 방어가 오히려 독이 됐을 것이다.
 * 댓글 수·첨부 수를 페치 조인이 아니라 <b>집계 쿼리</b>로 따로 구하는 이유가 그것이다
 * ({@code CommentRepository.countsByPostIds} · {@link FreeAttachmentRepository}).
 */
public interface FreePostRepository
        extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    /** 목록 — {@code PostSpecifications.search(...)} 와 함께 쓴다. 개수 쿼리에는 그래프가 붙지 않는다. */
    @Override
    @EntityGraph(attributePaths = {"author", "category"})
    Page<Post> findAll(Specification<Post> spec, Pageable pageable);

    /**
     * 상세 — 작성자·분류를 함께 읽는다.
     *
     * <p>{@code boardType} 을 조건에 넣는 것은 다른 게시판의 글 id 로 자유게시판 화면에
     * 들어오는 우회를 막기 위해서다(공통 {@code PostRepository.findByIdAndBoardType} 와 같은 규칙).
     */
    @EntityGraph(attributePaths = {"author", "category"})
    Optional<Post> findWithAuthorAndCategoryByIdAndBoardType(Long id, BoardType boardType);
}
