package com.ebstudy.portal.board.free;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.PageResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 자유게시판 사용자 API — 요구사항 4장.
 *
 * <p>컨트롤러는 얇다. 여기서 하는 일은 <b>파라미터를 모아 서비스에 넘기고 상태 코드를 정하는 것</b>
 * 뿐이고, 판정·검증·권한은 전부 서비스에 있다. {@code try-catch} 도 하지 않는다 —
 * {@code ApiException} 은 {@code GlobalExceptionHandler} 가 Problem Details 로 바꾼다.
 *
 * <p>★ <b>등록·수정이 둘 다 {@code POST} 인 이유</b>(수정에 {@code PUT} 을 쓰지 않았다):
 * 첨부 때문에 {@code multipart/form-data} 를 쓰는데, 톰캣은 멀티파트 본문의 <b>일반 필드</b>를
 * {@code POST} 에서만 파싱한다({@code parseBodyMethods} 기본값). {@code PUT} 으로 두면
 * 제목·내용이 조용히 {@code null} 로 들어온다. 규약보다 <b>동작</b>을 택했다.
 */
@RestController
@RequestMapping("/api/free-posts")
@RequiredArgsConstructor
public class FreeBoardController {

    private final FreeBoardService freeBoardService;
    private final FreeCommentService commentService;
    private final FreeAttachmentService attachmentService;

    /** 생성 응답 — 화면은 이 id 로 상세로 이동한다(목록 번호가 아니다). */
    public record CreatedIdResponse(Long id) {
    }

    // ── 목록 ────────────────────────────────────────────────

    /** 요구사항 1.1 · 4.1. 비로그인도 볼 수 있다(요구사항 1.3). */
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

    /** 메인 페이지(요구사항 2장) — 자유게시판 최신 5개. 기간 조건이 없다. */
    @GetMapping("/latest")
    public List<FreePostListItem> latest(
            @RequestParam(value = "limit", required = false, defaultValue = "5") int limit) {
        return freeBoardService.latest(limit);
    }

    /** 요구사항 1.1 — 검색 바의 분류 드롭다운. */
    @GetMapping("/categories")
    public List<FreeCategoryItem> categories() {
        return freeBoardService.categories();
    }

    // ── 상세 ────────────────────────────────────────────────

    /** 요구사항 4.2 + 1.4(조회수 증가). */
    @GetMapping("/{postId}")
    public FreePostDetail detail(@PathVariable("postId") Long postId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return freeBoardService.detail(postId, principal);
    }

    // ── 등록 · 수정 · 삭제 ───────────────────────────────────

    /** 요구사항 4.3 등록. 로그인 필요 — 서버가 확인한다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreatedIdResponse> create(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Long id = freeBoardService.create(principal,
                new FreePostWriteRequest(categoryId, title, content, List.of(), files));
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedIdResponse(id));
    }

    /** 요구사항 4.3 수정 — 본인만. {@code PUT} 이 아닌 이유는 클래스 주석 참조. */
    @PostMapping(path = "/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> update(@PathVariable("postId") Long postId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "removeAttachmentIds", required = false) List<Long> removeIds,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        freeBoardService.update(principal, postId,
                new FreePostWriteRequest(categoryId, title, content, removeIds, files));
        return ResponseEntity.noContent().build();
    }

    /** 요구사항 4.2 삭제 — 본인만. */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@PathVariable("postId") Long postId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        freeBoardService.delete(principal, postId);
        return ResponseEntity.noContent().build();
    }

    // ── 첨부 ────────────────────────────────────────────────

    /**
     * 요구사항 4.2 — <b>이미지 파일이라도 다운로드로 처리한다</b>(인라인 표시 금지).
     *
     * <p>세 가지를 함께 건다. 하나만으로는 부족하다:
     * <ul>
     *   <li>{@code Content-Disposition: attachment} — 브라우저에 "열지 말고 받아라"</li>
     *   <li>{@code Content-Type: application/octet-stream} — 저장된 타입을 그대로 실으면
     *       {@code image/png} 가 되어 주소를 직접 열었을 때 렌더될 여지가 남는다</li>
     *   <li>{@code X-Content-Type-Options: nosniff} — 내용을 보고 타입을 추측하는 것을 막는다.
     *       추측을 허용하면 위 두 개를 우회해 HTML 로 해석되는 경로가 생긴다(저장형 XSS)</li>
     * </ul>
     *
     * <p>파일명은 UTF-8 로 인코딩해서 넣는다 — 한글 이름이 그대로 나가면 헤더가 깨지고
     * 브라우저마다 다른 이름으로 저장된다.
     */
    @GetMapping("/{postId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable("postId") Long postId,
            @PathVariable("attachmentId") Long attachmentId) {
        FreeAttachmentService.Download download = attachmentService.download(postId, attachmentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.sizeBytes())
                .body(download.resource());
    }

    // ── 댓글 ────────────────────────────────────────────────

    /** 요구사항 4.2 — 목록은 누구나 본다. 상세 응답에도 함께 실리므로 갱신용 보조 경로다. */
    @GetMapping("/{postId}/comments")
    public List<FreeCommentItem> comments(@PathVariable("postId") Long postId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return commentService.list(postId, principal);
    }

    /** 요구사항 4.2 — 작성은 로그인한 사용자만. */
    @PostMapping("/{postId}/comments")
    public ResponseEntity<CreatedIdResponse> addComment(@PathVariable("postId") Long postId,
            @RequestBody FreeCommentWriteRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Long id = commentService.create(postId, principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedIdResponse(id));
    }

    /** 요구사항 1.3 — 본인 댓글, 그리고 관리자. */
    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable("postId") Long postId,
            @PathVariable("commentId") Long commentId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        commentService.delete(postId, commentId, principal);
        return ResponseEntity.noContent().build();
    }
}
