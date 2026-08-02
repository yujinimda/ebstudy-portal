package com.ebstudy.portal.board.free;

import com.ebstudy.portal.board.common.Attachment;
import com.ebstudy.portal.board.common.AttachmentRepository;
import com.ebstudy.portal.board.common.AttachmentStorage;
import com.ebstudy.portal.board.common.AttachmentValidator;
import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.Post;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 자유게시판 첨부 — 요구사항 4.2(다운로드) · 4.3(등록·수정 시 저장·삭제).
 *
 * <p>글 서비스에서 떼어낸 이유는 크기 때문이 아니라 <b>다루는 위험이 다르기</b> 때문이다.
 * 여기만 파일시스템을 만지고, 여기만 <b>롤백해도 되돌아오지 않는 부작용</b>을 만든다.
 *
 * <p>★ 이 클래스의 핵심은 <b>DB 트랜잭션과 디스크를 어긋나지 않게 맞추는 것</b>이다.
 * 파일 쓰기·지우기는 트랜잭션이 아니다. 순진하게 짜면 두 가지 사고가 난다:
 * <ol>
 *   <li>업로드 도중 뒤쪽 검증이 실패 → DB 는 롤백, <b>디스크에는 파일이 남는다</b>(유령 파일)</li>
 *   <li>삭제 처리 중 나중에 예외 → DB 는 롤백해 행이 살아나는데 <b>파일은 이미 지워졌다</b>
 *       (링크는 있는데 열리지 않는 첨부)</li>
 * </ol>
 * 그래서 <b>지우기는 커밋 이후에, 되돌리기는 롤백 이후에</b> 한다
 * ({@link #deleteFilesAfterCommit} · {@link #deleteFilesOnRollback}).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FreeAttachmentService {

    private static final BoardType BOARD = BoardType.FREE;

    private final AttachmentRepository attachments;
    private final FreeAttachmentRepository attachmentCounts;
    private final AttachmentValidator validator;
    private final AttachmentStorage storage;

    /** 다운로드 응답에 필요한 것만 모은다 — 엔티티를 컨트롤러로 내보내지 않는다. */
    public record Download(Resource resource, String filename, long sizeBytes) {
    }

    // ── 조회 ────────────────────────────────────────────────

    /** 목록의 첨부 아이콘 — 한 번의 집계 쿼리로 페이지 전체를 센다(N+1 회피). */
    Map<Long, Long> countsByPostIds(List<Long> postIds) {
        return attachmentCounts.countsByPostIds(postIds);
    }

    /** 상세·수정 화면의 첨부 목록. */
    List<FreeAttachmentItem> itemsOf(Long postId) {
        return attachments.findByPostIdOrderBySortOrderAscIdAsc(postId).stream()
                .map(attachment -> FreeAttachmentItem.of(attachment, postId))
                .toList();
    }

    /**
     * 요구사항 4.2 첨부 다운로드.
     *
     * <p>{@code postId} 를 함께 받아 소유 관계를 확인하는 것이 중요하다. 첨부 id 만으로 열어 주면
     * <b>번호를 하나씩 올려 가며 남의 글 첨부를 전부 받아 갈 수 있다</b>.
     */
    @Transactional(readOnly = true)
    public Download download(Long postId, Long attachmentId) {
        Attachment attachment = attachments.findById(attachmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.ATTACHMENT_NOT_FOUND));
        if (!attachment.belongsTo(postId)) {
            throw new ApiException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        // 다른 게시판 글의 id 로 이 경로에 들어오는 것도 막는다 — 게시판마다 공개 범위가 다르다
        // (문의게시판 비밀글). 프록시를 한 번 초기화하는 비용은 그 안전을 살 값어치가 있다
        if (attachment.getPost().getBoardType() != BOARD) {
            throw new ApiException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        return new Download(storage.load(attachment.getStoredPath()),
                attachment.getOriginalName(), attachment.getSizeBytes());
    }

    // ── 변경 ────────────────────────────────────────────────

    /**
     * 새 파일을 저장한다. 호출 쪽 트랜잭션 안에서 돈다.
     *
     * @param existingCount 이미 붙어 있는 개수. 요구사항 4.3 의 "최대 5개" 는 새로 올리는 수가
     *                      아니라 <b>합계</b>다 — 이 값이 없으면 5개짜리 글에 5개를 더 붙일 수 있다
     */
    void store(Post post, List<MultipartFile> files, int existingCount, OffsetDateTime now) {
        // 개수·확장자·크기 검증이 먼저다. 한 개라도 정책 위반이면 아무것도 저장하지 않는다
        validator.validate(BOARD, files, existingCount);
        if (files == null || files.isEmpty()) {
            return;
        }

        // ★ 목록을 먼저 등록하고 뒤에 채운다 — 루프 도중 실패해도 그때까지 쓴 파일이 지워진다
        List<String> writtenPaths = new ArrayList<>();
        deleteFilesOnRollback(writtenPaths);

        int order = existingCount;
        for (MultipartFile file : files) {
            AttachmentStorage.StoredFile stored = storage.store(BOARD, file);
            writtenPaths.add(stored.storedPath());
            attachments.save(Attachment.create(post, stored, order++, now));
        }
    }

    /**
     * 요구사항 4.3 — 수정 화면의 <b>개별 삭제</b>.
     *
     * <p>넘어온 id 가 정말 이 글의 첨부인지 하나하나 확인한다. 확인하지 않으면
     * 자기 글을 수정하면서 <b>남의 글 첨부를 지우는</b> 요청이 통한다.
     */
    void remove(Post post, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        Set<Long> unique = new HashSet<>(attachmentIds);
        List<Attachment> targets = attachments.findAllById(unique);
        if (targets.size() != unique.size()) {
            // 없는 id 가 섞였다 — 조용히 넘기면 화면은 "지워졌다"고 믿는다
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
        for (Attachment target : targets) {
            if (!target.belongsTo(post.getId())) {
                throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
            }
        }
        attachments.deleteAll(targets);
        deleteFilesAfterCommit(targets.stream().map(Attachment::getStoredPath).toList());
    }

    /**
     * 남은 첨부의 순서를 0부터 다시 매기고 개수를 돌려준다.
     *
     * <p>중간을 지우면 {@code 0,2,3} 같은 구멍이 남는다. 자유게시판은 순서를 화면에 쓰지 않지만,
     * 새로 붙일 파일의 시작 번호를 "개수" 로 계산하므로 구멍이 있으면 <b>번호가 겹친다</b>.
     */
    int renumber(Long postId) {
        // 앞의 deleteAll 은 아직 DB 에 안 갔을 수 있다. 쿼리 실행 전 자동 flush 가
        // 그것을 반영하므로 여기서 읽으면 지워진 것이 빠진 목록이 온다
        List<Attachment> remaining = attachments.findByPostIdOrderBySortOrderAscIdAsc(postId);
        int order = 0;
        for (Attachment attachment : remaining) {
            attachment.reorder(order++);
        }
        return remaining.size();
    }

    /**
     * 글 삭제에 딸린 파일 정리.
     *
     * <p>{@code attachments} <b>행</b>은 V8 의 {@code ON DELETE CASCADE} 가 지운다.
     * 디스크 파일은 DB 가 모른다 — 그래서 경로를 미리 읽어 두고 커밋 뒤에 지운다.
     */
    void removeFilesOf(Long postId) {
        // 경로만 읽는다 — 엔티티로 읽으면 뒤이은 글 삭제가 커밋에서 깨진다
        // (AttachmentRepository.findStoredPathsByPostId 주석 참조)
        deleteFilesAfterCommit(attachments.findStoredPathsByPostId(postId));
    }

    // ── 트랜잭션과 디스크 맞추기 ──────────────────────────────

    /** 커밋이 확정된 뒤에만 지운다 — 롤백됐는데 파일이 없어지는 사고를 막는다. */
    private void deleteFilesAfterCommit(List<String> storedPaths) {
        if (storedPaths.isEmpty()) {
            return;
        }
        List<String> snapshot = List.copyOf(storedPaths);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 트랜잭션 밖에서 불린 경우(테스트 등) — 미룰 곳이 없으니 지금 지운다
            storage.deleteAll(snapshot);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.deleteAll(snapshot);
            }
        });
    }

    /**
     * 롤백됐을 때 방금 쓴 파일을 되돌린다.
     *
     * <p>{@code afterCompletion} 을 쓰는 이유: {@code afterCommit} 은 커밋 때만 불린다.
     * 우리가 관심 있는 것은 <b>커밋되지 않은 경우</b>다.
     */
    private void deleteFilesOnRollback(List<String> writtenPaths) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED || writtenPaths.isEmpty()) {
                    return;
                }
                log.warn("자유게시판 첨부 업로드가 롤백됐다 — 저장했던 파일을 지운다 count={}",
                        writtenPaths.size());
                storage.deleteAll(List.copyOf(writtenPaths));
            }
        });
    }
}
