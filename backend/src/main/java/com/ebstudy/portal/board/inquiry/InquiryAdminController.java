package com.ebstudy.portal.board.inquiry;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.PageResponse;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 문의게시판 API — 요구사항 6.5.
 *
 * <p>경로가 {@code /api/admin/**} 이라 {@code SecurityConfig} 가 이미 {@code ROLE_ADMIN} 을
 * 요구한다. 그럼에도 서비스가 {@code requireAdmin} 을 <b>다시</b> 부른다 — 설정 한 줄이
 * 바뀌면 조용히 열리는 구조를 만들지 않는다(001 {@code FR-018} 과 같은 이중 방어).
 *
 * <p><b>답변 삭제 경로가 없는 것은 설계다</b>(FR-034 · AC-47). 지우면 상태가 미답변으로
 * 되돌아가 작성자의 수정·삭제 권한이 되살아난다. 삭제를 요청하면 405 가 나간다.
 */
@RestController
@RequestMapping("/api/admin/inquiries")
@RequiredArgsConstructor
public class InquiryAdminController {

    private final InquiryService inquiries;
    private final InquiryAnswerService answers;

    /** 1. 관리자 목록 — 분류 없음. "나의 문의내역" 은 받지 않는다. */
    @GetMapping
    public PageResponse<InquiryListItemResponse> list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return inquiries.listForAdmin(from, to, keyword, page, size, sort, direction, principal);
    }

    /** 2. 관리자 상세 — 비밀글도 비밀번호 없이 본다(AC-31). <b>조회수는 올리지 않는다</b>. */
    @GetMapping("/{id}")
    public InquiryDetailResponse detail(@PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return inquiries.adminDetail(id, principal);
    }

    /** 3. 답변 등록 — 문의당 하나(AC-45). 이미 있으면 거부한다. */
    @PostMapping("/{id}/answer")
    public ResponseEntity<InquiryAnswerResponse> createAnswer(@PathVariable Long id,
            @RequestBody InquiryAnswerRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(answers.create(id, request == null ? null : request.content(), principal));
    }

    /** 4. 답변 수정 — 상태는 답변완료 그대로다(AC-47 ①). */
    @PutMapping("/{id}/answer")
    public InquiryAnswerResponse updateAnswer(@PathVariable Long id,
            @RequestBody InquiryAnswerRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return answers.update(id, request == null ? null : request.content(), principal);
    }

    /** 5. 글 삭제 — 답변 여부와 무관하다(미답변 제한은 작성자에게만 적용된다). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        inquiries.deleteByAdmin(id, principal);
        return ResponseEntity.noContent().build();
    }
}
