package com.ebstudy.portal.board.inquiry;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.auth.ratelimit.ClientIpResolver;
import com.ebstudy.portal.board.common.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 문의게시판 API — 요구사항 6.1~6.4.
 *
 * <p>컨트롤러는 얇다. 여기서 하는 일은 <b>HTTP 를 벗기는 것</b>뿐이고
 * 판정(권한 · 비밀글 · 검증 · 시도 제한)은 전부 {@link InquiryService} 안에 있다.
 * {@code try-catch} 도 없다 — {@code ApiException} 은
 * {@code common/GlobalExceptionHandler} 가 하나의 형식으로 바꾼다.
 *
 * <p>⚠️ <b>이 컨트롤러의 조회 경로는 인증이 없어야 한다</b>(FR-001 · AC-1 = 001 {@code AC-25}).
 * 그런데 {@code auth/SecurityConfig} 는 아직 {@code .anyRequest().authenticated()} 다 →
 * 그대로 두면 목록·상세가 401 이 된다. {@code auth/} 는 이 단계의 담당 밖이라 손대지 않았다.
 * 보고서의 최우선 항목이다.
 */
@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    /**
     * 열람 통과를 <b>헤더</b>로 주고받는다 — 질의 문자열에 실으면 접근 로그·리퍼러·북마크에
     * 남는다({@link SecretReadGrantService}).
     */
    public static final String GRANT_HEADER = "X-Inquiry-Grant";

    private final InquiryService inquiries;
    private final ClientIpResolver clientIps;

    /** 1. 목록 — 인증 불필요. {@code mine=true} 만 로그인을 요구한다(AC-14). */
    @GetMapping
    public PageResponse<InquiryListItemResponse> list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return inquiries.list(from, to, keyword, mine, page, size, sort, direction, principal);
    }

    /** 2. 메인 페이지용 최신 N개 — 요구사항 2장. 인증 불필요. */
    @GetMapping("/latest")
    public List<InquiryListItemResponse> latest(
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return inquiries.latest(limit, principal);
    }

    /**
     * 3. 상세 — 인증 불필요.
     * 비밀글이고 권한이 없으면 403 이다(AC-28). 그때 화면은 4번으로 잠금 안내를 받고
     * 5번으로 비밀번호를 보낸다.
     */
    @GetMapping("/{id}")
    public InquiryDetailResponse detail(@PathVariable Long id,
            @RequestHeader(value = GRANT_HEADER, required = false) String grantToken,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return inquiries.detail(id, grantToken, principal);
    }

    /** 4. 잠금 안내 — 목록에 이미 공개된 항목만. 조회수를 올리지 않는다. */
    @GetMapping("/{id}/preview")
    public InquiryDetailResponse preview(@PathVariable Long id) {
        return inquiries.preview(id);
    }

    /**
     * 5. 비밀글 잠금해제 — AC-29.
     * 성공하면 {@code grantToken} 이 나온다. 이후 3번에 {@code X-Inquiry-Grant} 로 실어 보낸다.
     */
    @PostMapping("/{id}/unlock")
    public InquiryUnlockResponse unlock(@PathVariable Long id,
            @RequestBody InquiryUnlockRequest body,
            @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request) {
        // 시도 제한의 "클라이언트" 축은 요청 출처 주소로 센다.
        // 프록시 뒤에서 무엇을 출처로 볼지는 001 이 이미 정해 두었다(신뢰 프록시 목록 기반)
        return inquiries.unlock(id, body == null ? null : body.password(), principal,
                clientIps.resolve(request));
    }

    /** 6. 등록 — 로그인 필요(AC-16 · AC-17). */
    @PostMapping
    public ResponseEntity<InquiryDetailResponse> create(@RequestBody InquiryCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inquiries.create(request, principal));
    }

    /** 7. 수정 — 본인 + 미답변일 때만(AC-19 · AC-20). */
    @PutMapping("/{id}")
    public InquiryDetailResponse update(@PathVariable Long id,
            @RequestBody InquiryUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return inquiries.update(id, request, principal);
    }

    /** 8. 삭제 — 본인 + 미답변일 때만(AC-21). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        inquiries.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
