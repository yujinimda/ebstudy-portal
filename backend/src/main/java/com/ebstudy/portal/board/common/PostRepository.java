package com.ebstudy.portal.board.common;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 게시글 조회 — 4종 공통.
 *
 * <p>{@link JpaSpecificationExecutor} 를 붙인 이유: 요구사항 1.1 의 목록 조건(기간·분류·검색어·
 * 나의 글)이 <b>선택적으로 조합</b>된다. 조합마다 메서드를 만들면 16개가 되고, 문자열로 JPQL 을
 * 이어 붙이면 SQL 인젝션 경로가 생긴다. 조건 조립은 {@link PostSpecifications} 한 곳에서만 한다.
 *
 * <p>게시판별 패키지는 이 파일을 고치지 않는다 — 필요한 쿼리는 자기 패키지에 별도
 * 리포지토리 인터페이스를 만들어 추가한다(요구사항 9장 파일 경계).
 */
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    /** 다른 게시판의 글 id 로 우회하는 것을 막는다 — id 만으로 찾지 않는다. */
    Optional<Post> findByIdAndBoardType(Long id, BoardType boardType);

    /** 요구사항 1.1 "글 번호는 전체 게시글 수 기준 역순" 의 그 전체 수. */
    long countByBoardType(BoardType boardType);

    /**
     * 요구사항 3.1 — 상단 고정은 최대 5개, 넘게 등록되면 <b>최신 5개만</b> 노출한다.
     * "최대 5개" 를 등록 시점에 막지 않고 조회에서 자르는 것은 요구사항 문구 그대로다.
     *
     * <p>★ V11 이후 {@code pinned} 는 {@link NoticePost} 에 있어서 <b>메서드 이름 규칙으로는
     * 만들 수 없다</b>({@code Post} 에 그런 칸이 없다). JPQL 로 {@code NoticePost} 를
     * 직접 조회한다 — 공지가 아닌 글은 타입 자체로 걸러지므로 {@code board_type} 조건도 필요 없다.
     */
    @Query("""
            select n from NoticePost n
             where n.pinned = true
             order by n.createdAt desc, n.id desc
            """)
    List<Post> findPinnedNotices(Pageable pageable);

    /** 메인 페이지(요구사항 2장) — 게시판별 최신 N개. */
    List<Post> findByBoardTypeOrderByCreatedAtDescIdDesc(BoardType boardType, Pageable pageable);

    /**
     * 조회수 증가 — 요구사항 1.4.
     *
     * <p>{@code Post.increaseViewCount()} 와 달리 <b>DB 에서 더한다</b>.
     * 읽은 값에 더하는 방식은 동시 요청 하나가 묻히지만 이쪽은 묻히지 않는다.
     * 상세 화면이 자주 열리는 게시판에서 이 메서드를 쓰고, 영속성 컨텍스트에 이미 올라온
     * 엔티티와 값이 어긋나므로 <b>같은 트랜잭션에서 그 엔티티를 다시 읽지 않는다</b>.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :id")
    int increaseViewCount(@Param("id") Long id);

    /** 요구사항 7.2 — "이미 사용 중인 분류" 판정. 삭제 대신 비활성으로 내릴지 결정하는 근거다. */
    boolean existsByCategoryId(Long categoryId);
}
