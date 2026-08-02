package com.ebstudy.portal.board.inquiry;

import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.Post;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 문의게시판 전용 조회 — {@code board.common.PostRepository} 를 <b>고치지 않고</b> 옆에 둔다
 * (요구사항 9장 파일 경계. 같은 엔티티에 리포지토리가 여럿인 것은 정상이다).
 *
 * <p>존재 이유는 하나, {@code @EntityGraph} 다.
 * <ul>
 *   <li><b>N+1 제거</b> — 목록 한 페이지가 최대 50행이고 행마다 등록자 이름이 필요하다.
 *       {@code author} 가 지연 로딩이면 <b>행 수만큼 SELECT</b> 가 더 나간다</li>
 *   <li><b>지연 로딩 예외 제거</b> — {@code open-in-view=false} 라 트랜잭션이 끝나면 프록시를
 *       건드릴 수 없다. 목록·상세 조회를 트랜잭션 없이(리포지토리 호출 단위로) 두려면
 *       엔티티가 <b>반환 시점에 이미 완성</b>되어 있어야 한다.
 *       ★ 이 성질 덕분에 조회 경로가 트랜잭션을 열지 않고, 그래서 무차별 대입 방어의
 *       <b>지연이 트랜잭션 밖</b>에 놓인다(DB 커넥션을 붙잡고 자지 않는다)</li>
 * </ul>
 */
public interface InquiryPostRepository
        extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    /** 목록 — 조건 조립은 {@link InquirySpecifications} 가 한다. */
    @Override
    @EntityGraph(attributePaths = "author")
    Page<Post> findAll(Specification<Post> spec, Pageable pageable);

    /** 다른 게시판의 글 번호로 우회하는 것을 막는다 — id 만으로 찾지 않는다. */
    @EntityGraph(attributePaths = "author")
    Optional<Post> findByIdAndBoardType(Long id, BoardType boardType);

    /** 메인 페이지(요구사항 2장) — 최신 5개. */
    @EntityGraph(attributePaths = "author")
    List<Post> findByBoardTypeOrderByCreatedAtDescIdDesc(BoardType boardType, Pageable pageable);

    long countByBoardType(BoardType boardType);
}
