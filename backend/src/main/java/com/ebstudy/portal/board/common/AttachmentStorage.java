package com.ebstudy.portal.board.common;

import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨부 저장소 — 파일이 <b>어디에</b> 어떻게 놓이는지를 서비스가 몰라도 되게 한다.
 *
 * <p>인터페이스로 두는 이유는 미래의 S3 때문이 아니라 <b>지금의 테스트</b> 때문이다.
 * 서비스 테스트가 실제 디스크를 건드리지 않아도 된다.
 *
 * <p>★ 이 인터페이스의 핵심 계약 두 가지:
 * <ol>
 *   <li><b>원본 파일명을 파일시스템에 쓰지 않는다.</b> 사용자가 보낸 이름에는
 *       {@code ../../etc/passwd} 나 널 바이트가 들어올 수 있다. 구현체는 <b>스스로 이름을 만들고</b>
 *       원본 이름은 DB 의 {@code original_name} 에만 둔다(다운로드 시 보여주기용)</li>
 *   <li><b>돌려주는 경로는 루트 기준 상대 경로다.</b> 절대 경로를 DB 에 넣으면 저장소를 옮길 때
 *       모든 행이 깨지고, 무엇보다 그 값이 응답에 새면 서버 디렉터리 구조가 노출된다</li>
 * </ol>
 */
public interface AttachmentStorage {

    /**
     * 파일 하나를 저장한다.
     *
     * <p>호출 전에 {@code AttachmentValidator} 로 확장자·크기·개수를 검증해야 한다 —
     * 구현체도 마지막 방어선으로 다시 확인하지만, 검증 실패를 사용자에게 알리는 것은 서비스의 일이다.
     */
    StoredFile store(BoardType boardType, MultipartFile file);

    /** 요구사항 4.2 첨부 다운로드 · 5.2 캐러셀 이미지. */
    Resource load(String storedPath);

    /**
     * 파일을 지운다.
     *
     * <p>없는 파일이어도 예외를 던지지 않는다 — 삭제는 <b>멱등</b>해야 한다.
     * DB 행은 사라졌는데 파일 삭제가 실패해 사용자 동작 전체가 롤백되면,
     * 사용자는 "지웠는데 안 지워진다" 를 겪는다(V8 헤더의 판단과 같은 방향).
     */
    void delete(String storedPath);

    /** 글 삭제 시 여러 개를 한 번에. 하나 실패해도 나머지를 계속 지운다. */
    void deleteAll(List<String> storedPaths);

    /**
     * @param originalName 사용자에게 보여줄 이름(정제 완료)
     * @param storedPath   저장소 루트 기준 <b>상대</b> 경로
     * @param contentType  확장자에서 정한 값. 클라이언트가 보낸 값이 아니다
     */
    record StoredFile(String originalName, String storedPath, String contentType, long sizeBytes) {
    }
}
