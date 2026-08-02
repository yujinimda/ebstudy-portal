package com.ebstudy.portal.board.notice;

import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.Post;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 공지사항 전용 조회 — {@code board.common.PostRepository} 를 고치지 않기 위한 별도 인터페이스다
 * (요구사항 9장 파일 경계). 같은 엔티티에 리포지토리가 둘 있어도 문제가 없다.
 *
 * <p>여기 있는 것은 공통 리포지토리에 없는 두 가지뿐이다.
 * <ol>
 *   <li><b>연관 즉시 로딩</b> — {@code Post.category}·{@code Post.author} 는 LAZY 이고
 *       {@code spring.jpa.open-in-view=false} 라 서비스 트랜잭션 밖에서는 초기화되지 않는다.
 *       목록 10건을 그냥 매핑하면 조회 1 + 연관 N 번이 나간다(N+1).
 *       조건 조립은 {@code PostSpecifications} 가 계속 맡고, 여기서는 <b>id 목록만 받아
 *       한 번에 채운다</b> — 조건과 로딩을 분리해야 목록 쿼리 자리가 갈라지지 않는다.</li>
 *   <li><b>고정 글 총 개수</b> — 요구사항 3.1 "5개를 넘게 등록하면 최신 5개만 노출".
 *       관리 화면이 "지금 몇 개가 고정되어 있는지"를 알아야 6번째를 고정할 때 경고할 수 있다.</li>
 * </ol>
 */
public interface NoticeRepository extends Repository<Post, Long> {

    /**
     * 상세 1건 — 분류·작성자까지 한 번에 읽는다.
     *
     * <p>{@code boardType} 을 조건에 넣는 이유: 자유게시판 글의 id 를
     * {@code /api/notices/{id}} 로 넣어 우회하는 것을 막는다.
     */
    @Query("select p from Post p left join fetch p.category left join fetch p.author "
            + "where p.id = :id and p.boardType = :boardType")
    Optional<Post> findDetail(@Param("id") Long id, @Param("boardType") BoardType boardType);

    /**
     * 목록에 쓸 글들의 연관을 한 방에 채운다.
     *
     * <p>반환값을 쓰지 않아도 같은 트랜잭션의 1차 캐시가 같은 인스턴스를 돌려주므로
     * 원본 목록의 프록시도 함께 초기화된다. 그래도 <b>반환값으로 맵을 만들어 쓴다</b> —
     * 그 미묘함에 기대는 코드는 나중에 읽는 사람이 지운다.
     */
    @Query("select p from Post p left join fetch p.category left join fetch p.author "
            + "where p.id in :ids")
    List<Post> findAllWithAssociations(@Param("ids") List<Long> ids);

    /** 요구사항 3.1 — 고정된 글이 5개를 넘었는지 관리 화면이 알아야 한다. */
    long countByBoardTypeAndPinnedTrue(BoardType boardType);

    /**
     * 목록의 <b>순서를 유지한 채</b> 연관을 채운다.
     *
     * <p>{@code in :ids} 는 순서를 보장하지 않는다. 정렬은 이미
     * {@code BoardSearchCriteria.toPageable()} 이 정한 것이므로 여기서 다시 정렬하지 않고
     * <b>원본 순서대로 다시 세운다</b> — 여기서 순서가 바뀌면 화면의 정렬 선택이 무시된다.
     */
    default List<Post> withAssociations(List<Post> posts) {
        if (posts.isEmpty()) {
            return posts;
        }
        Map<Long, Post> loaded = findAllWithAssociations(posts.stream().map(Post::getId).toList())
                .stream()
                .collect(Collectors.toMap(Post::getId, Function.identity(), (first, second) -> first));
        return posts.stream().map(post -> loaded.getOrDefault(post.getId(), post)).toList();
    }
}
