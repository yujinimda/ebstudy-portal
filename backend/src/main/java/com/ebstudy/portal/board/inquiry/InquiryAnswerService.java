package com.ebstudy.portal.board.inquiry;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.BoardAccessGuard;
import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.InquiryAnswer;
import com.ebstudy.portal.board.common.InquiryAnswerRepository;
import com.ebstudy.portal.board.common.Post;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import com.ebstudy.portal.user.User;
import com.ebstudy.portal.user.UserRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 답변 — 요구사항 6.5 · FR-030~FR-034.
 *
 * <h2>답변 삭제가 없는 것은 누락이 아니다 (FR-034 · AC-47)</h2>
 * "답변완료" 는 답변 <b>행의 존재 여부</b>로 판정하고(V9), 작성자의 수정·삭제 권한은
 * 그 상태에 묶여 있다(FR-015). 답변을 지우면 상태가 <b>미답변으로 되돌아가고</b>
 * 그 순간 작성자의 수정 권한이 되살아난다 — 관리자가 오타를 지우려다
 * <b>질문이 통째로 바뀔 수 있는 문</b>이 열린다. 오타는 수정으로 고치고,
 * 문의 자체를 없애야 하면 글 삭제({@code InquiryService.deleteByAdmin})를 쓴다.
 * 그래서 이 클래스에 삭제 메서드를 두지 않았고, 컨트롤러에도 경로가 없다(요청하면 405).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InquiryAnswerService {

    private final InquiryPostRepository posts;
    private final InquiryAnswerRepository answers;
    private final InquiryAnswerViewRepository answerViews;
    private final UserRepository users;
    private final BoardAccessGuard guard;

    /** AC-43 · AC-44 · AC-45 · AC-46. */
    @Transactional
    public InquiryAnswerResponse create(Long postId, String rawContent,
            AuthenticatedUser principal) {
        guard.requireAdmin(principal);
        Post post = posts.findByIdAndBoardType(postId, BoardType.INQUIRY)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
        String content = InquiryTexts.requireAnswerContent(rawContent);

        // 사전 확인은 관리자에게 정확한 이유를 주기 위한 것이고,
        // 실제 유일성 판정은 V9 의 uk_inquiry_answers_post_id 가 한다(관리자 둘이 동시에 답변)
        if (answers.existsByPostId(postId)) {
            throw new ApiException(ErrorCode.INQUIRY_ANSWER_ALREADY_EXISTS);
        }
        User admin = currentUser(principal);
        try {
            InquiryAnswer saved = answers.saveAndFlush(
                    InquiryAnswer.create(post, admin, content, OffsetDateTime.now()));
            log.info("문의 답변 등록 postId={} adminId={}", postId, admin.getId());
            return InquiryAnswerResponse.of(saved);
        } catch (DataIntegrityViolationException ex) {
            // 001 AC-28 — 원본 메시지(테이블명·제약조건명)를 밖으로 내보내지 않는다.
            // 여기서 잡지 않으면 스프링 기본 동작이 그것을 그대로 흘리고 500 이 된다
            throw new ApiException(ErrorCode.INQUIRY_ANSWER_ALREADY_EXISTS);
        }
    }

    /**
     * AC-47 ① — 답변은 고칠 수 있다. 상태는 <b>답변완료 그대로</b>다.
     *
     * <p>답변자를 이번에 고친 관리자로 갱신한다({@code InquiryAnswer.update} 가 그렇게 만들어져
     * 있다) — 화면에 보이는 이름은 <b>마지막으로 그 내용에 책임진 사람</b>이어야 한다.
     */
    @Transactional
    public InquiryAnswerResponse update(Long postId, String rawContent,
            AuthenticatedUser principal) {
        guard.requireAdmin(principal);
        String content = InquiryTexts.requireAnswerContent(rawContent);
        InquiryAnswer answer = answerViews.findByPostId(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.INQUIRY_ANSWER_NOT_FOUND));
        answer.update(currentUser(principal), content, OffsetDateTime.now());
        log.info("문의 답변 수정 postId={}", postId);
        return InquiryAnswerResponse.of(answer);
    }

    private User currentUser(AuthenticatedUser principal) {
        return users.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED));
    }
}
