package com.ebstudy.portal.board.category;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.BoardType;
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
 * 분류 관리 — 요구사항 7.2. 관리자만({@code SecurityConfig} 가 {@code /api/admin/**} 를 이미 막는다).
 *
 * <p>컨트롤러는 얇다. 판단(권한·중복·삭제 가부)은 전부 {@link CategoryAdminService} 에 있다.
 * 여기서 {@code try-catch} 하지 않는다 — 실패는 {@code ApiException} 으로 올라가
 * {@code GlobalExceptionHandler} 가 Problem Details 로 만든다.
 */
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
public class CategoryAdminController {

    private final CategoryAdminService categoryAdmin;

    /**
     * @param sortOrder 생략하면 그 게시판의 맨 뒤에 붙는다
     */
    public record CreateRequest(String boardType, String name, Integer sortOrder) {
    }

    /**
     * {@code null} 인 항목은 바꾸지 않는다 — "사용 안 함" 토글만 하는 화면이
     * 이름·순서를 함께 보내지 않아도 되게 한 것이다.
     *
     * <p>{@code boardType} 이 없는 것은 의도다 — 등록 후 게시판은 바꾸지 않는다
     * ({@code Category.boardType} 이 {@code updatable = false}).
     */
    public record UpdateRequest(String name, Integer sortOrder, Boolean active) {
    }

    /** 비활성까지 전부. 각 항목에 {@code postCount}·{@code deletable} 이 함께 온다. */
    @GetMapping
    public List<CategoryAdminResponse> list(@RequestParam("boardType") String boardType,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return categoryAdmin.list(BoardType.from(boardType), principal);
    }

    @PostMapping
    public ResponseEntity<CategoryAdminResponse> create(@RequestBody CreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        CategoryAdminResponse created = categoryAdmin.create(BoardType.from(request.boardType()),
                request.name(), request.sortOrder(), principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{categoryId}")
    public CategoryAdminResponse update(@PathVariable("categoryId") Long categoryId,
            @RequestBody UpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return categoryAdmin.update(categoryId, request.name(), request.sortOrder(),
                request.active(), principal);
    }

    /**
     * 요구사항 7.2 — <b>글이 하나라도 쓰고 있으면 거부</b>한다. 그때는 사용 여부를
     * {@code false} 로 바꾸는 {@link #update} 를 쓴다.
     *
     * <p>거부는 {@code ErrorCode.CATEGORY_IN_USE} 로 <b>409</b> 다 — 요청 형식은 멀쩡하고
     * 지금 자원의 상태 때문에 거절되는 것이라 400 이 아니다.
     */
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(@PathVariable("categoryId") Long categoryId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        categoryAdmin.delete(categoryId, principal);
        return ResponseEntity.noContent().build();
    }
}
