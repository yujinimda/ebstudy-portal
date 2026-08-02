package com.ebstudy.portal.board.category;

import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.Category;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 관리 화면에만 필요한 분류 쿼리.
 *
 * <p>{@code board.common.CategoryRepository} 에 이미 있는 것은 여기에 다시 만들지 않는다.
 * 그 파일을 고치지 않는 것이 요구사항 9장의 파일 경계 규칙이라 <b>모자란 두 개만</b> 여기 둔다.
 */
public interface CategoryAdminRepository extends Repository<Category, Long> {

    /**
     * 이름 중복 사전 확인 — <b>자기 자신은 뺀다</b>.
     *
     * <p>이게 없으면 "자유"를 "자유"로 다시 저장하는 것(대소문자만 바꾸는 것 포함)이
     * 중복으로 거부된다. 유일성의 진짜 판정은 V5 의 {@code uk_categories_board_type_name}
     * 유니크 인덱스가 하고, 이 조회는 사용자에게 정확한 코드를 주기 위한 것이다
     * (동시 등록 경합은 인덱스가 잡는다 — {@code SignupService} 와 같은 구조).
     *
     * <p>{@code lower()} 로 비교하는 이유: 그 인덱스가 {@code LOWER(name)} 함수 인덱스라
     * 같은 모양으로 물어야 인덱스를 탄다(V1·V5 와 같은 규칙).
     */
    @Query("select count(c) > 0 from Category c where c.boardType = :boardType "
            + "and lower(c.name) = lower(:name) and c.id <> :excludedId")
    boolean existsOtherWithName(@Param("boardType") BoardType boardType,
            @Param("name") String name, @Param("excludedId") Long excludedId);

    /**
     * 표시 순서를 안 적고 등록했을 때 <b>맨 뒤</b>에 붙이기 위한 값.
     * 기본 0 으로 두면 순서를 안 정한 분류가 전부 맨 앞에 몰린다(V5 주석의 그 함정).
     */
    @Query("select coalesce(max(c.sortOrder), 0) from Category c where c.boardType = :boardType")
    int maxSortOrder(@Param("boardType") BoardType boardType);
}
