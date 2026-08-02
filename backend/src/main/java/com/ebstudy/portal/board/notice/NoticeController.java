package com.ebstudy.portal.board.notice;

import com.ebstudy.portal.auth.AuthenticatedUser;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공지사항 — 사용자 화면. 요구사항 3.1 · 3.2.
 *
 * <p>컨트롤러는 얇다. 여기서 하는 일은 <b>문자열을 값으로 바꾸는 것</b>뿐이고
 * 판단은 전부 {@link NoticeService} 가 한다. {@code try-catch} 를 쓰지 않는다 —
 * {@code ApiException} 은 {@code common/GlobalExceptionHandler} 가 받는다.
 *
 * <p>⚠️ 요구사항 1.3 은 목록·상세 조회를 <b>누구나</b> 할 수 있게 했지만
 * {@code auth/SecurityConfig} 는 {@code .anyRequest().authenticated()} 다.
 * 이 경로들이 {@code permitAll} 로 열리기 전에는 비로그인 요청이 401 이 된다.
 * {@code auth/} 는 이 담당의 파일 경계 밖이라 손대지 않았다 → 보고서에 올렸다.
 */
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    /**
     * 목록 — 요구사항 3.1. 모든 파라미터가 선택이다(기본값은 {@code BoardSearchCriteria} 가 정한다).
     *
     * <p>파라미터를 전부 {@code String} 으로 받는 이유는 {@link NoticeRequestParams} 에 적었다
     * (타입 변환 실패가 500 으로 나가는 것을 막는다).
     */
    @GetMapping
    public NoticeListResponse list(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) String page,
            @RequestParam(value = "size", required = false) String size,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "direction", required = false) String direction) {
        OffsetDateTime now = OffsetDateTime.now();
        return noticeService.list(NoticeRequestParams.toCriteria(from, to, categoryId, keyword,
                page, size, sort, direction, now), now);
    }

    /**
     * 분류 목록 — 요구사항 1.1 의 분류 드롭다운.
     *
     * <p>{@code /{id}} 보다 위에 있어야 한다고 착각하기 쉬운데 아니다 — 스프링은 리터럴 경로를
     * 경로 변수보다 먼저 맞춘다. 그래도 순서를 이렇게 둔 것은 읽는 사람을 위한 것이다.
     */
    @GetMapping("/categories")
    public List<NoticeCategoryResponse> categories() {
        return noticeService.activeCategories();
    }

    /** 메인 페이지(요구사항 2장) — 공지사항 최신 5개. 기간 조건이 없다. */
    @GetMapping("/latest")
    public List<NoticeListItem> latest(
            @RequestParam(name = "limit", required = false, defaultValue = "5") int limit) {
        return noticeService.latest(limit, OffsetDateTime.now());
    }

    /** 상세 — 요구사항 3.2. 조회수가 증가한다(요구사항 1.4). */
    @GetMapping("/{id}")
    public NoticeDetailResponse detail(@PathVariable("id") String id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return noticeService.read(NoticeRequestParams.id(id), principal);
    }
}
