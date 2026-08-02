package com.ebstudy.portal.board.gallery;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 갤러리 관리 API — 요구사항 5.4 · 7.1.
 *
 * <p>{@code /api/admin/**} 는 {@code SecurityConfig} 가 이미 {@code hasRole("ADMIN")} 으로 막는다.
 * 그런데도 서비스에서 {@link com.ebstudy.portal.board.common.BoardAccessGuard#requireAdmin} 을
 * 다시 부르는 것은 중복이 아니라 <b>의도</b>다 — 경로 설정 하나가 바뀌면 이 API 들이 통째로
 * 열린다. 권한은 경로가 아니라 동작에 붙어 있어야 한다.
 *
 * <p>등록·수정 엔드포인트를 두지 않은 것도 판단이다. 요구사항 5.4 는 관리 화면에
 * <b>목록만</b> 정의했고(공지사항 3.3 이 등록·수정을 명시한 것과 대비된다),
 * 갤러리 글의 주인은 사용자다. 관리자가 스스로 글을 쓸 때는 사용자 API 를 쓴다.
 */
@RestController
@RequestMapping("/api/admin/galleries")
@RequiredArgsConstructor
public class GalleryAdminController {

    private final GalleryService galleryService;

    /** 요구사항 5.4 — 번호 · 분류 · 제목(썸네일 + 파일 개수) · 조회 · 등록일시 · 등록자. */
    @GetMapping
    public PageResponse<GalleryAdminRowResponse> list(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "categoryId", required = false) String categoryId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "direction", required = false) String direction,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return galleryService.listForAdmin(GalleryController.criteria(from, to, categoryId, keyword,
                page, size, sort, direction, principal), principal);
    }

    /** 관리 상세 — <b>조회수를 올리지 않는다</b>(관리 열람이 통계를 오염시키지 않게). */
    @GetMapping("/{id}")
    public GalleryDetailResponse read(@PathVariable("id") String id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return galleryService.readForAdmin(GalleryQueryParams.optionalLong(id), principal);
    }

    /** 관리자는 남의 글도 지운다 — 사용자 API 의 {@code requireOwner} 와 갈라지는 유일한 지점이다. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        galleryService.deleteByAdmin(GalleryQueryParams.optionalLong(id), principal);
    }
}
