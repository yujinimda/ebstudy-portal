package com.ebstudy.portal.board.common;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 분류 조회.
 *
 * <p>게시판별 패키지에서 <b>추가 쿼리가 필요하면 자기 패키지에 별도 인터페이스를 만든다</b> —
 * 같은 엔티티에 리포지토리가 여러 개 있어도 문제가 없다. 이 파일을 고치지 않는 것이
 * 병렬 작업의 파일 경계 규칙(요구사항 9장)이다.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 사용자 목록 화면의 분류 드롭다운 — 비활성은 뺀다(요구사항 7.2). */
    List<Category> findByBoardTypeAndActiveTrueOrderBySortOrderAscIdAsc(BoardType boardType);

    /** 관리 화면 — 비활성까지 전부 본다. */
    List<Category> findByBoardTypeOrderBySortOrderAscIdAsc(BoardType boardType);

    /** 다른 게시판의 분류 id 를 넘겨 우회하는 것을 막는다 — id 만으로 찾지 않는다. */
    Optional<Category> findByIdAndBoardType(Long id, BoardType boardType);

    /**
     * 중복 이름 사전 확인. 유일성 판정 자체는 V5 의 {@code LOWER(name)} 유니크 인덱스가 한다
     * — 조회도 {@code lower()} 로 해야 그 인덱스를 탄다(V1·UserRepository 와 같은 규칙).
     */
    @Query("select count(c) > 0 from Category c "
            + "where c.boardType = :boardType and lower(c.name) = lower(:name)")
    boolean existsByBoardTypeAndNameIgnoringCase(@Param("boardType") BoardType boardType,
            @Param("name") String name);
}
