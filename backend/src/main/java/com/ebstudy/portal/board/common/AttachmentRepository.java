package com.ebstudy.portal.board.common;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /** 상세 화면 — 요구사항 5.2 캐러셀 순서, 4.2 첨부 목록. */
    List<Attachment> findByPostIdOrderBySortOrderAscIdAsc(Long postId);

    /** 요구사항 5.1 · 2장 — 갤러리 목록의 썸네일은 <b>첫 번째</b> 이미지다. */
    Optional<Attachment> findFirstByPostIdOrderBySortOrderAscIdAsc(Long postId);

    /** 요구사항 4.3 · 5.3 — 최대 개수 검증의 "이미 올린 개수". */
    int countByPostId(Long postId);

    /**
     * ★ 목록의 썸네일·첨부 아이콘·"+8" 개수를 한 번에 구한다.
     * 행마다 부르면 N+1 이 된다({@code CommentRepository.countGroupedByPostIds} 와 같은 이유).
     */
    List<Attachment> findByPostIdInOrderByPostIdAscSortOrderAscIdAsc(List<Long> postIds);

    /** 글 삭제 시 파일까지 지우려면 경로를 먼저 읽어야 한다 — 행은 CASCADE 로 사라지지만 파일은 아니다(V8). */
    List<Attachment> findByPostId(Long postId);

    /**
     * ★ 글을 <b>지우기 직전</b>에 파일 경로만 뽑는 전용 질의. 엔티티로 읽지 않는 것이 핵심이다.
     *
     * <p>{@link #findByPostId} 로 읽으면 {@code Attachment} 가 영속성 컨텍스트에 올라온다.
     * 그 상태로 {@code Post} 를 지우면 커밋 시점에 Hibernate 가 <b>"삭제된 Post 를 참조하는
     * 살아 있는 Attachment"</b> 를 발견하고 {@code TransientPropertyValueException} 을 던진다
     * — 첨부가 있는 글의 삭제가 전부 500 이 된다(자유게시판·갤러리 양쪽에서 실제로 그랬다).
     *
     * <p>{@code Post} 에는 첨부로 가는 {@code @OneToMany} 가 없다(연관을 걸 수 없는 이유는
     * {@code Post} 주석 참조). 행은 V8 의 {@code ON DELETE CASCADE} 가 지우므로 <b>JPA 는 이
     * 행들을 알 필요가 없고, 알면 오히려 방해가 된다.</b> 그래서 스칼라만 가져온다.
     */
    @Query("select a.storedPath from Attachment a where a.post.id = :postId")
    List<String> findStoredPathsByPostId(@Param("postId") Long postId);
}
