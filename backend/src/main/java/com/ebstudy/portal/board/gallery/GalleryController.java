package com.ebstudy.portal.board.gallery;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.BoardSearchCriteria;
import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.PageResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

/**
 * 갤러리 사용자 API — 요구사항 5장.
 *
 * <p>컨트롤러는 얇다. 여기서 하는 일은 <b>문자열을 값으로 바꾸고</b> HTTP 로 되돌리는 것뿐이고,
 * 권한·길이·개수·확장자 판정은 전부 {@link GalleryService} 안에 있다.
 * {@code try-catch} 도 하지 않는다 — {@code ApiException} 은 전역 핸들러가 받는다.
 *
 * <p>⚠️ 목록·상세·이미지는 요구사항 1.3 상 <b>누구나</b> 볼 수 있어야 하는데,
 * 현재 {@code auth/SecurityConfig} 는 {@code anyRequest().authenticated()} 다.
 * {@code GET /api/galleries/**} 를 {@code permitAll} 로 열지 않으면 비로그인 사용자에게 401 이 나간다.
 * {@code auth/} 는 이 작업의 담당 밖이라 <b>보고만</b> 한다.
 */
@RestController
@RequestMapping("/api/galleries")
@RequiredArgsConstructor
public class GalleryController {

    /**
     * 목록 썸네일은 카드마다 한 번씩, 스크롤할 때마다 다시 불린다.
     *
     * <p>저장 파일은 UUID 이름으로 한 번 쓰고 <b>덮어쓰지 않으므로</b>({@code LocalAttachmentStorage})
     * URL 이 같으면 내용도 같다 → {@code immutable} 을 붙여 브라우저가 재검증조차 하지 않게 한다.
     * 이미지가 지워지면 URL 자체가 목록에서 사라지므로 캐시가 오래 남아도 문제가 되지 않는다.
     */
    private static final CacheControl IMAGE_CACHE =
            CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable();

    /** {@code HttpHeaders} 에 상수가 없다. 브라우저가 Content-Type 을 추측하지 못하게 막는다. */
    private static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    private final GalleryService galleryService;

    /** 요구사항 5.1 — 카드형 목록(검색·정렬·페이징). 비로그인도 본다. */
    @GetMapping
    public PageResponse<GalleryCardResponse> list(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "categoryId", required = false) String categoryId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "direction", required = false) String direction,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return galleryService.list(criteria(from, to, categoryId, keyword, page, size, sort,
                direction, principal), principal);
    }

    /** 요구사항 1.1 — 목록 화면의 분류 드롭다운(활성만). */
    @GetMapping("/categories")
    public List<GalleryCategoryResponse> categories() {
        return galleryService.categories();
    }

    /** 메인 페이지(요구사항 2장) — 갤러리 최신 3개 + 썸네일 + {@code +N}. */
    @GetMapping("/latest")
    public List<GalleryCardResponse> latest(
            @RequestParam(name = "limit", required = false) String limit) {
        return galleryService.latest(GalleryQueryParams.optionalInt(limit));
    }

    /** 요구사항 5.2 — 상세(이미지 전체를 순서대로). 조회수가 오른다(1.4). */
    @GetMapping("/{id}")
    public GalleryDetailResponse read(@PathVariable("id") String id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return galleryService.read(GalleryQueryParams.optionalLong(id), principal);
    }

    /**
     * 이미지 바이너리 — 목록 썸네일과 상세 캐러셀이 같은 경로를 쓴다.
     *
     * <p>자유게시판(요구사항 4.2)이 이미지도 <b>다운로드</b>로 강제하는 것과 달리 갤러리는
     * {@code inline} 이다. 갤러리는 이미지를 <b>보여주는 것이 목적</b>이기 때문이다.
     * 대신 두 가지로 방어한다: {@code Content-Type} 은 클라이언트가 보낸 값이 아니라
     * 확장자에서 정한 값이고(jpg/gif/png 뿐이라 스크립트가 실행될 여지가 없다),
     * {@code nosniff} 로 브라우저의 추측을 막는다.
     */
    @GetMapping("/{id}/images/{imageId}")
    public ResponseEntity<Resource> image(@PathVariable("id") String id,
            @PathVariable("imageId") String imageId, WebRequest webRequest,
            HttpServletResponse response) {
        GalleryImageFile file = galleryService.readImage(GalleryQueryParams.optionalLong(id),
                GalleryQueryParams.optionalLong(imageId));

        if (webRequest.checkNotModified(file.eTag(), file.lastModified())) {
            // 304 에도 캐시 지시를 실어야 다음 요청이 또 조건부로 들어오지 않는다.
            // 여기서 null 을 돌려주는 것은 "응답을 이미 다 썼다"는 스프링 MVC 의 약속이다
            response.setHeader(HttpHeaders.CACHE_CONTROL, IMAGE_CACHE.getHeaderValue());
            return null;
        }

        return ResponseEntity.ok()
                .cacheControl(IMAGE_CACHE)
                .eTag(file.eTag())
                .lastModified(file.lastModified())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.originalName(), StandardCharsets.UTF_8).toString())
                .header(CONTENT_TYPE_OPTIONS, "nosniff")
                .body(file.resource());
    }

    /**
     * 요구사항 5.3 등록 — {@code multipart/form-data}.
     *
     * @param images 이미지 파트. jpg/gif/png · 개당 1MB · 최대 20개 · <b>최소 1장</b>.
     *               순서는 <b>보낸 순서</b>이고 첫 장이 썸네일이 된다
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> create(
            @RequestParam(name = "categoryId", required = false) String categoryId,
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "content", required = false) String content,
            @RequestPart(name = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Long id = galleryService.create(principal, write(categoryId, title, content), images);
        return ResponseEntity.created(URI.create("/api/galleries/" + id)).build();
    }

    /**
     * 요구사항 5.3 수정 — 분류·제목·내용과 이미지 집합·순서를 <b>한 요청에서</b> 바꾼다.
     *
     * @param imageIds 남길 기존 이미지 id 를 <b>보여줄 순서대로</b> 쉼표로 이은 값
     *                 (예: {@code 12,9,31}). 여기 없는 기존 이미지는 지워진다.
     *                 빈 문자열이면 기존 이미지를 전부 뺀다. 파라미터를 아예 보내지 않으면
     *                 지금 순서 그대로 전부 유지한다
     * @param images   뒤에 이어 붙일 새 이미지
     */
    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> update(@PathVariable("id") String id,
            @RequestParam(name = "categoryId", required = false) String categoryId,
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "content", required = false) String content,
            @RequestParam(name = "imageIds", required = false) String imageIds,
            @RequestPart(name = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        galleryService.update(GalleryQueryParams.optionalLong(id), principal,
                write(categoryId, title, content),
                GalleryQueryParams.optionalLongList(imageIds), images);
        return ResponseEntity.noContent().build();
    }

    /** 요구사항 1.3 — 본인 글만. 화면의 확인 창은 편의일 뿐이고 판정은 서버가 한다. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        galleryService.delete(GalleryQueryParams.optionalLong(id), principal);
    }

    private static GalleryWriteRequest write(String categoryId, String title, String content) {
        return new GalleryWriteRequest(GalleryQueryParams.optionalLong(categoryId), title, content);
    }

    /**
     * 목록 조건 조립 — 검증은 전부 {@link BoardSearchCriteria#of} 안에 있다.
     * 게시판마다 다시 검증하면 4벌이 되고 4벌은 갈라진다.
     */
    static BoardSearchCriteria criteria(String from, String to, String categoryId, String keyword,
            String page, String size, String sort, String direction, AuthenticatedUser principal) {
        return BoardSearchCriteria.of(
                BoardType.GALLERY,
                GalleryQueryParams.optionalDateTime(from, false),
                GalleryQueryParams.optionalDateTime(to, true),
                GalleryQueryParams.optionalLong(categoryId),
                keyword,
                // 갤러리에는 "나의 글만 보기" 가 없다(요구사항 6.1 은 문의게시판 전용) → 항상 꺼 둔다
                Boolean.FALSE,
                principal == null ? null : principal.userId(),
                GalleryQueryParams.optionalInt(page),
                GalleryQueryParams.optionalInt(size),
                sort,
                direction,
                OffsetDateTime.now());
    }
}
