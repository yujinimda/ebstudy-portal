package com.ebstudy.portal.board.category;

import com.ebstudy.portal.board.common.Post;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * "이 분류를 쓰는 글이 있는가" 만 묻는다 — 요구사항 7.2 의 삭제 가부 판정.
 *
 * <p>{@code board.common.PostRepository} 를 고치지 않고 여기에 따로 만든 이유는
 * 요구사항 9장의 파일 경계 규칙이다. 같은 엔티티에 리포지토리가 여러 개 있어도 문제없다.
 *
 * <p>{@code JpaRepository} 가 아니라 {@link Repository} 를 상속한 것도 의도다 —
 * 분류 관리가 <b>글을 저장·삭제할 수 있는 통로</b>를 갖게 하고 싶지 않다.
 * 여기서 노출되는 것은 읽기 두 개뿐이다.
 */
public interface CategoryUsageRepository extends Repository<Post, Long> {

    /** 단건 판정 — 삭제 직전에 부른다. */
    boolean existsByCategoryId(Long categoryId);

    /**
     * 관리 목록용 일괄 집계.
     *
     * <p>분류마다 {@code existsByCategoryId} 를 부르면 분류 수만큼 쿼리가 나간다(N+1).
     * 분류가 열 개 남짓이라 당장은 티가 안 나지만, 목록 화면은 열릴 때마다 부르는 자리다 —
     * "여기는 원래 한 방"이라는 규칙을 처음부터 박아 둔다.
     */
    @Query("select p.category.id as categoryId, count(p) as total from Post p "
            + "where p.category.id in :categoryIds group by p.category.id")
    List<CategoryUsage> countByCategoryIds(@Param("categoryIds") Collection<Long> categoryIds);

    /** 프로젝션 — 엔티티를 통째로 읽지 않는다. */
    interface CategoryUsage {
        Long getCategoryId();

        Long getTotal();
    }
}
