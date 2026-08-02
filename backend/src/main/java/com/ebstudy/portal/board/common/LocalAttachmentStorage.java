package com.ebstudy.portal.board.common;

import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬 파일시스템 구현 — {@code board.attachment.root} 아래에 둔다.
 *
 * <p>저장 이름은 <b>서버가 만든다</b>: {@code <게시판>/<yyyy>/<MM>/<UUID>.<확장자>}.
 * <ul>
 *   <li>UUID — 원본 이름을 쓰지 않으므로 경로 순회·덮어쓰기·이름 충돌이 원천적으로 없다</li>
 *   <li>연·월 디렉터리 — 한 디렉터리에 파일이 수십만 개 쌓이면 파일시스템 조회가 느려진다</li>
 * </ul>
 *
 * <p>★ 저장·읽기·삭제 <b>모든 경로에서</b> "루트 안인가" 를 다시 확인한다({@link #resolveInsideRoot}).
 * DB 에 담긴 값이면 안전하다고 가정하지 않는다 — 그 가정이 깨지는 순간이 바로 사고다.
 */
@Component
@Slf4j
public class LocalAttachmentStorage implements AttachmentStorage {

    private final Path root;
    private final AttachmentValidator validator;

    public LocalAttachmentStorage(BoardProperties properties, AttachmentValidator validator) {
        String configured = properties.attachment().root();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("board.attachment.root 가 비어 있다");
        }
        this.root = Path.of(configured).toAbsolutePath().normalize();
        this.validator = validator;
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            // 여기서 죽는 편이 낫다 — 첫 업로드까지 문제를 미루면 사용자가 발견하게 된다
            throw new IllegalStateException("첨부 저장소 디렉터리를 만들 수 없다", ex);
        }
        log.info("첨부 저장소 루트 root={}", root);
    }

    @Override
    public StoredFile store(BoardType boardType, MultipartFile file) {
        AttachmentPolicy policy = AttachmentPolicy.of(boardType);
        // 서비스가 이미 검증했더라도 여기서 다시 본다 — 저장소가 스스로를 지킨다
        String extension = validator.validateOne(policy, file);
        String originalName = AttachmentFilenames.sanitizeOriginalName(file.getOriginalFilename());

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String relativePath = "%s/%04d/%02d/%s.%s".formatted(
                boardType.name().toLowerCase(Locale.ROOT),
                now.getYear(), now.getMonthValue(),
                UUID.randomUUID(), extension);

        Path target = resolveInsideRoot(relativePath);
        try {
            Files.createDirectories(target.getParent());
            // transferTo 대신 InputStream 복사를 쓴다 — 일부 구현에서 transferTo 의 상대경로
            // 해석이 임시 디렉터리 기준이라 예상 밖의 위치에 쓰인다
            try (var in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException ex) {
            log.error("첨부 저장 실패 path={}", relativePath, ex);
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        return new StoredFile(originalName, relativePath, policy.contentTypeOf(extension),
                file.getSize());
    }

    @Override
    public Resource load(String storedPath) {
        Path target = resolveInsideRoot(storedPath);
        if (!Files.isRegularFile(target)) {
            throw new ApiException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String storedPath) {
        try {
            Files.deleteIfExists(resolveInsideRoot(storedPath));
        } catch (IOException ex) {
            // 파일이 남는 것은 디스크 낭비로 끝나지만, 여기서 예외를 던지면 사용자의 삭제가
            // 통째로 실패한다(V8 헤더의 판단과 같은 방향) → 로그만 남기고 넘어간다
            log.warn("첨부 파일 삭제 실패 path={}", storedPath, ex);
        }
    }

    @Override
    public void deleteAll(List<String> storedPaths) {
        if (storedPaths == null) {
            return;
        }
        storedPaths.forEach(this::delete);
    }

    /**
     * ★ 경로 순회 방어의 마지막 방어선.
     *
     * <p>{@code normalize()} 로 {@code ..} 를 접은 <b>뒤에</b> 루트 안인지 본다.
     * 접기 전에 검사하면 {@code a/../../b} 같은 값이 통과한다.
     */
    private Path resolveInsideRoot(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            // 여기 온다는 것은 DB 값이 오염됐거나 코드가 바뀐 것이다. 사용자에게는 알리지 않는다
            log.error("첨부 경로가 저장소 밖을 가리킨다 path={}", relativePath);
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        return resolved;
    }
}
