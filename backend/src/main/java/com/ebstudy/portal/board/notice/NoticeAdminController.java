package com.ebstudy.portal.board.notice;

import com.ebstudy.portal.auth.AuthenticatedUser;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
 * 공지사항 — 관리자 화면. 요구사항 3.3 · 7.1.
 *
 * <p>사용자 API 와 <b>경로부터</b> 나눈 이유: {@code SecurityConfig} 가
 * {@code /api/admin/**} 를 {@code hasRole("ADMIN")} 으로 막고 있다(요구사항 7.1).
 * 같은 컨트롤러에 읽기·쓰기를 섞으면 그 경로 규칙이 쓰기에만 걸리게 만들 방법이 없다.
 * 권한은 <b>경로(1차)와 서비스(2차)</b> 두 겹으로 검증한다.
 */
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class NoticeAdminController {

    private final NoticeAdminService noticeAdminService;

    /** 관리 목록 — 요구사항 3.3. 고정 글도 이 목록에 섞여 나온다. */
    @GetMapping
    public NoticeAdminListResponse list(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) String page,
            @RequestParam(value = "size", required = false) String size,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "direction", required = false) String direction,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        OffsetDateTime now = OffsetDateTime.now();
        return noticeAdminService.list(NoticeRequestParams.toCriteria(from, to, categoryId, keyword,
                page, size, sort, direction, now), principal, now);
    }

    /** 등록/수정 폼의 분류 선택 — 비활성 분류까지 포함한다. */
    @GetMapping("/categories")
    public List<NoticeCategoryResponse> categories(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return noticeAdminService.allCategories(principal);
    }

    /** 수정 폼이 채울 값. <b>조회수를 올리지 않는다</b>(운영 행위는 조회가 아니다). */
    @GetMapping("/{id}")
    public NoticeDetailResponse detail(@PathVariable("id") String id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return noticeAdminService.read(NoticeRequestParams.id(id), principal);
    }

    /**
     * 등록 — 요구사항 3.3.
     *
     * <p>{@code "정말로 등록 하시겠습니까?"} 확인(요구사항 1.2)은 <b>화면의 몫</b>이다.
     * 서버가 확인 절차를 강제할 수단은 없고, 강제하려면 2단계 요청이 되어 요구사항에 없는
     * 복잡도가 생긴다.
     */
    @PostMapping
    public ResponseEntity<NoticeDetailResponse> create(@RequestBody NoticeWriteRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        NoticeDetailResponse created = noticeAdminService.create(request, principal,
                OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** 수정 — 요구사항 3.3. 보낸 값으로 <b>전부</b> 덮는다(부분 수정이 아니라 PUT 인 이유). */
    @PutMapping("/{id}")
    public NoticeDetailResponse update(@PathVariable("id") String id,
            @RequestBody NoticeWriteRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return noticeAdminService.update(NoticeRequestParams.id(id), request, principal,
                OffsetDateTime.now());
    }

    /** 삭제 — 요구사항 3.3. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        noticeAdminService.delete(NoticeRequestParams.id(id), principal);
        return ResponseEntity.noContent().build();
    }
}
