package com.ebstudy.portal.board.free;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.PageResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자유게시판 관리 API — 요구사항 7.1 좌측 LNB <i>"자유 게시판 관리"</i>.
 *
 * <p>사용자 API 와 <b>같은 목록·상세</b>를 쓰되 두 가지가 다르다:
 * <ul>
 *   <li>상세에서 <b>조회수를 올리지 않는다</b> — 관리자 열람으로 지표가 부풀지 않게</li>
 *   <li>글 삭제가 <b>소유자 검증 없이</b> 관리자 권한으로 열린다(요구사항 1.3 이 관리자 예외를
 *       댓글에만 준 것과 별개로, 관리 화면의 삭제는 요구사항 3.3 · 6.5 와 같은 성격의 동작이다)</li>
 * </ul>
 *
 * <p>글 <b>등록·수정</b>이 없는 것은 의도다 — 요구사항 0장이 자유게시판의 등록 주체를
 * "사용자" 로 못박았다. 관리자가 사용자 이름으로 글을 쓰는 경로를 만들지 않는다.
 *
 * <p>댓글 삭제 전용 경로도 두지 않았다. {@code DELETE /api/free-posts/{postId}/comments/{id}} 가
 * 이미 관리자를 통과시키므로(요구사항 1.3) 같은 판정을 두 벌로 만들 이유가 없다.
 *
 * <p>인증은 이중이다: {@code SecurityConfig} 가 {@code /api/admin/**} 를 {@code ROLE_ADMIN} 으로
 * 막고, 서비스가 {@code BoardAccessGuard.requireAdmin} 으로 다시 확인한다.
 * 경로 규칙 하나가 바뀌어도 구멍이 나지 않게 하기 위해서다.
 */
@RestController
@RequestMapping("/api/admin/free-posts")
@RequiredArgsConstructor
public class FreeBoardAdminController {

    private final FreeBoardService freeBoardService;

    @GetMapping
    public PageResponse<FreePostListItem> list(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "direction", required = false) String direction,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return freeBoardService.list(new FreePostListQuery(from, to, categoryId, keyword, page,
                size, sort, direction), principal);
    }

    /** 관리 화면의 분류 필터도 같은 목록을 쓴다(사용 중인 분류만). */
    @GetMapping("/categories")
    public List<FreeCategoryItem> categories() {
        return freeBoardService.categories();
    }

    /** 조회수를 올리지 않는 상세. */
    @GetMapping("/{postId}")
    public FreePostDetail detail(@PathVariable("postId") Long postId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return freeBoardService.detailForAdmin(postId, principal);
    }

    /** 요구사항 1.2 "정말로 삭제 하시겠습니까" 확인은 화면의 일이다. 서버는 권한만 본다. */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@PathVariable("postId") Long postId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        freeBoardService.deleteByAdmin(principal, postId);
        return ResponseEntity.noContent().build();
    }
}
