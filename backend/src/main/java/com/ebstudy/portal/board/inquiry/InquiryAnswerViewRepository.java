package com.ebstudy.portal.board.inquiry;

import com.ebstudy.portal.board.common.InquiryAnswer;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

/**
 * 답변 <b>표시용</b> 조회 — 답변자 이름을 함께 가져온다(006 Assumptions 5:
 * 답변에 관리자 이름과 답변일시를 보여준다).
 *
 * <p>{@code board.common.InquiryAnswerRepository} 를 고치지 않고 옆에 둔 이유는
 * {@link InquiryPostRepository} 와 같다 — {@code admin} 이 지연 로딩이라
 * 트랜잭션 밖에서 이름을 읽을 수 없다.
 *
 * <p>메서드를 하나만 두려고 {@code JpaRepository} 가 아니라 {@code Repository} 를 상속했다.
 * 저장·삭제는 공통 리포지토리가 이미 제공하고, <b>여기에 쓰기 수단을 열면</b>
 * 같은 엔티티의 저장 경로가 둘이 된다.
 */
public interface InquiryAnswerViewRepository extends Repository<InquiryAnswer, Long> {

    @EntityGraph(attributePaths = "admin")
    Optional<InquiryAnswer> findByPostId(Long postId);
}
