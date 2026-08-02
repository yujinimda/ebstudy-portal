package com.ebstudy.portal.board.common;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InquiryAnswerRepository extends JpaRepository<InquiryAnswer, Long> {

    /** 요구사항 6.3 — 답변이 없으면 화면이 "아직 등록된 답변이 없습니다." 를 보여준다. */
    Optional<InquiryAnswer> findByPostId(Long postId);

    /** 요구사항 6.3 "수정·삭제는 미답변일 때만" 의 판정. */
    boolean existsByPostId(Long postId);

    /**
     * 목록의 "답변완료 / 미답변" 표기(요구사항 6.1) — 답변이 있는 글 id 만 한 번에 받는다.
     * 행마다 {@link #existsByPostId(Long)} 를 부르면 N+1 이다.
     */
    @Query("select a.post.id from InquiryAnswer a where a.post.id in :postIds")
    List<Long> findAnsweredPostIds(@Param("postIds") List<Long> postIds);
}
