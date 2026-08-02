package com.ebstudy.portal.board.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 첨부 — V8 {@code attachments}. 자유게시판 첨부파일(4.3)과 갤러리 이미지(5.3)를 함께 담는다.
 *
 * <p>{@code originalName} 은 <b>보여주기용</b>이고 {@code storedPath} 는 <b>서버가 만든</b>
 * 이름이다. 둘을 섞으면 경로 순회 공격이 열린다({@code AttachmentStorage} 주석 참조).
 */
@Entity
@Table(name = "attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;

    /** 다운로드 파일명으로만 쓴다. <b>파일시스템 경로로 쓰지 않는다.</b> */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    /** 저장소 루트 기준 상대 경로. 서버가 UUID 로 만든 값만 들어간다(V8 CHECK 가 뒷받침한다). */
    @Column(name = "stored_path", nullable = false, length = 500, updatable = false)
    private String storedPath;

    /** 클라이언트가 보낸 값이 아니라 <b>확장자에서 정한</b> 값이다(위조된 Content-Type 을 믿지 않는다). */
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** 요구사항 5.3 — 0번이 갤러리 썸네일이다. 순서가 없으면 썸네일이 매번 달라진다. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private Attachment(Post post, String originalName, String storedPath, String contentType,
            long sizeBytes, int sortOrder, OffsetDateTime now) {
        this.post = post;
        this.originalName = originalName;
        this.storedPath = storedPath;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sortOrder = sortOrder;
        this.createdAt = now;
    }

    public static Attachment create(Post post, AttachmentStorage.StoredFile stored, int sortOrder,
            OffsetDateTime now) {
        return new Attachment(post, stored.originalName(), stored.storedPath(),
                stored.contentType(), stored.sizeBytes(), sortOrder, now);
    }

    /** 개별 삭제 뒤 남은 것들의 순서를 다시 매길 때 쓴다(요구사항 4.3 · 5.3). */
    public void reorder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean belongsTo(Long postId) {
        return postId != null && post != null && postId.equals(post.getId());
    }
}
