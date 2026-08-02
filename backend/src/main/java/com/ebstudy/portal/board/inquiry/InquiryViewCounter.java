package com.ebstudy.portal.board.inquiry;

import com.ebstudy.portal.board.common.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조회수 증가 — 요구사항 1.4 · FR-036 · AC-42.
 *
 * <p><b>왜 별도 빈인가.</b> 문의 조회 경로는 트랜잭션을 열지 않는다
 * ({@link InquiryPostRepository} 가 {@code @EntityGraph} 로 엔티티를 완성해 돌려주므로
 * 지연 로딩이 없다). 트랜잭션을 열지 않는 이유는 무차별 대입 방어의 <b>지연이 트랜잭션 밖</b>
 * 에서 일어나야 하기 때문이다 — 안에서 자면 DB 커넥션과 잠금을 붙잡는다.
 * 그런데 조회수 증가는 <b>쓰기</b>라 트랜잭션이 필요하다. 그래서 그 한 줄만 별도 빈으로 떼어
 * 프록시를 거치게 한다(같은 클래스 안에서 부르면 {@code @Transactional} 이 적용되지 않는다).
 *
 * <p>{@code Post.increaseViewCount()}(읽은 값에 +1) 가 아니라 <b>DB 에서 더하는 UPDATE</b> 를
 * 쓴다. 006 Edge Cases 는 동시 증가의 유실을 허용하지만, 어차피 별도 트랜잭션이 필요한
 * 상황이라면 유실이 없는 쪽을 고르지 않을 이유가 없다.
 */
@Service
@RequiredArgsConstructor
public class InquiryViewCounter {

    private final PostRepository posts;

    /**
     * ★ <b>열람에 성공했을 때만</b> 부른다. 실패한 비밀번호 시도로 조회수가 오르면
     * 무차별 대입 1만 번이 조회수 1만으로 찍혀 그 수치가 의미를 잃는다(AC-42).
     */
    @Transactional
    public void increase(Long postId) {
        posts.increaseViewCount(postId);
    }
}
