package com.ebstudy.portal.board.common;

import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import com.ebstudy.portal.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글 <b>공통</b> — V6 {@code posts} + V11 하위 표.
 *
 * <h2>왜 상속인가 (ADR-006 개정 · V11)</h2>
 * 처음에는 4종을 한 표에 담고 {@code board_type} 으로 구분했다. 그러면 {@code pinned}(공지) ·
 * {@code secret}(문의) 같은 <b>한 게시판에서만 의미 있는 칸</b>이 나머지 게시판 행에서도
 * 늘 자리를 차지한다. "이 칸은 저 게시판에서만 쓴다"를 CHECK 제약으로 지켜야 했는데,
 * 그것은 <b>스키마가 스스로 말하지 못한다</b>는 뜻이다.
 *
 * <p>V11 에서 {@code JOINED} 로 바꿨다 — 공통 칸은 {@code posts}, 전용 칸은 게시판별 표.
 * 이제 <b>자유게시판 글에 {@code pinned} 를 넣을 자리가 애초에 없다.</b>
 *
 * <p><b>왜 4종 전부 하위 표를 두나</b>: 전용 칸이 없는 자유·갤러리에도 표를 만든다.
 * {@code JOINED} 는 구상 클래스마다 표가 하나씩 있어야 한다. 대가는 조회 때 조인 하나이고,
 * 얻는 것은 규칙이 예외 없이 성립하는 것이다.
 *
 * <p><b>왜 표를 완전히 4개로 쪼개지 않았나</b>: {@code comments}·{@code attachments}·
 * {@code inquiry_answers} 가 "글 하나"를 참조한다. 완전히 쪼개면 그 참조가
 * <b>"어느 표의 어느 id"</b> 가 되어 외래키를 걸 수 없다. 공통 표가 남아야 FK 가 성립한다.
 *
 * <h2>이 클래스가 {@code board.common} 에 있는 이유</h2>
 * 게시판별 패키지에서 각자 {@code @Entity} 를 만들면 자식 테이블이 어느 엔티티를
 * 참조해야 하는지 정할 수 없다. 게시판별 패키지는 이 엔티티를 <b>쓰기만</b> 한다.
 */
@Entity
@Table(name = "posts")
// ★ board_type 이 구분자다. 값에 따라 어느 하위 표를 조인할지 JPA 가 정한다.
//   V6 의 ix_posts_board_type_created_at 인덱스가 그대로 목록 조회의 주 경로로 남는다.
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "board_type", discriminatorType = DiscriminatorType.STRING, length = 20)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 구분자 칸을 <b>읽기 전용</b>으로 한 번 더 매핑한 것이다.
     * 쓰는 쪽은 {@code @DiscriminatorValue} 이고, 여기는 코드가 값을 <b>읽기</b> 위한 창구다
     * ({@code insertable=false} 를 빼면 같은 칸에 두 주인이 생겨 부팅이 깨진다).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "board_type", nullable = false, length = 20,
            insertable = false, updatable = false)
    private BoardType boardType;

    /** 문의게시판만 {@code null} 이다(V6 {@code ck_posts_category}). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** 작성자는 바뀌지 않는다 — 소유자 검증(요구사항 1.3)의 기준값이다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    /** 요구사항 1.2 "100자 미만" → 99. 길이 제약을 DB 에도 둔다(V1 과 같은 규칙). */
    @Column(name = "title", nullable = false, length = 99)
    private String title;

    /** 요구사항 1.2 "4000자 미만" → 3999. */
    @Column(name = "content", nullable = false, length = 3999)
    private String content;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** 하위 클래스가 공통 칸을 채울 때 쓴다. */
    protected Post(Category category, User author, String title, String content,
            OffsetDateTime now) {
        this.category = category;
        this.author = author;
        this.title = title;
        this.content = content;
        this.viewCount = 0L;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ── 정적 팩토리 ────────────────────────────────────────────
    // 하위 타입을 돌려주지만 반환형은 Post 로 둔다 — 부르는 쪽이 바뀌지 않는다.
    // 팩토리를 게시판별로 나눈 것은 의도다: 자유게시판 글에 pinned 를 넘기는 호출이
    // 애초에 컴파일되지 않는다.

    /** 공지사항(003) — 등록 주체가 관리자인지는 {@code BoardAccessGuard} 가 본다. */
    public static Post notice(Category category, User author, String title, String content,
            boolean pinned, OffsetDateTime now) {
        requireCategoryOf(BoardType.NOTICE, category);
        return new NoticePost(category, author, title, content, pinned, now);
    }

    /** 자유게시판(002). */
    public static Post free(Category category, User author, String title, String content,
            OffsetDateTime now) {
        requireCategoryOf(BoardType.FREE, category);
        return new FreePost(category, author, title, content, now);
    }

    /** 갤러리(005). */
    public static Post gallery(Category category, User author, String title, String content,
            OffsetDateTime now) {
        requireCategoryOf(BoardType.GALLERY, category);
        return new GalleryPost(category, author, title, content, now);
    }

    /**
     * 문의게시판(006). 분류가 없다.
     *
     * @param secretPasswordHash 비밀글이면 해시, 아니면 {@code null}.
     *                           <b>원문을 넘기지 않는다</b> — 해시는 {@code SecretPostPassword} 가 만든다
     */
    public static Post inquiry(User author, String title, String content, String secretPasswordHash,
            OffsetDateTime now) {
        return new InquiryPost(author, title, content, secretPasswordHash, now);
    }

    /** 분류 없이 등록하려는 시도를 여기서 막는다 — DB 까지 가면 500 이 된다(V6 ck_posts_category). */
    private static void requireCategoryOf(BoardType boardType, Category category) {
        if (category == null || category.getBoardType() != boardType) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
    }

    // ── 공통 동작 ──────────────────────────────────────────────

    public void updateContent(String title, String content, OffsetDateTime now) {
        this.title = title;
        this.content = content;
        this.updatedAt = now;
    }

    public void changeCategory(Category category, OffsetDateTime now) {
        requireCategoryOf(this.boardType, category);
        this.category = category;
        this.updatedAt = now;
    }

    /** 하위 클래스가 자기 칸을 바꾼 뒤 부른다. */
    protected void touch(OffsetDateTime now) {
        this.updatedAt = now;
    }

    // ── 게시판 전용 칸에 대한 공통 창구 ────────────────────────
    // ★ 값은 하위 표가 들고 있지만, 목록·상세를 만드는 코드는 Post 로만 다룬다.
    //   여기서 instanceof 로 갈라 주면 부르는 쪽이 타입을 몰라도 된다.
    //   "그 게시판이 아니면 애초에 false" 가 자연스러운 답이라 예외를 던지지 않는다.

    /** 요구사항 3.1 알림글. 공지가 아니면 항상 {@code false}. */
    public boolean isPinned() {
        return this instanceof NoticePost notice && notice.pinned();
    }

    /** 요구사항 6.2 비밀글. 문의가 아니면 항상 {@code false}. */
    public boolean isSecret() {
        return this instanceof InquiryPost inquiry && inquiry.secret();
    }

    /** 비밀글 비밀번호 <b>해시</b>. 문의가 아니거나 비밀글이 아니면 {@code null}. */
    public String getSecretPasswordHash() {
        return this instanceof InquiryPost inquiry ? inquiry.secretPasswordHash() : null;
    }

    /** 요구사항 3.1 — 공지사항만. 다른 게시판이면 거부한다. */
    public void changePinned(boolean pinned, OffsetDateTime now) {
        if (!(this instanceof NoticePost notice)) {
            if (pinned) {
                throw new ApiException(ErrorCode.REQUEST_INVALID);
            }
            return;
        }
        notice.applyPinned(pinned, now);
    }

    /**
     * 요구사항 6.2 — 비밀글 설정/해제.
     * {@code secret} 과 해시가 따로 놀면 "비밀글인데 아무나 열리는" 상태가 된다 →
     * 두 값을 한 메서드에서만 바꾼다(V11 {@code ck_inquiry_posts_secret_password} 와 같은 규칙).
     */
    public void changeSecret(String secretPasswordHash, OffsetDateTime now) {
        if (!(this instanceof InquiryPost inquiry)) {
            if (secretPasswordHash != null) {
                throw new ApiException(ErrorCode.REQUEST_INVALID);
            }
            return;
        }
        inquiry.applySecret(secretPasswordHash, now);
    }

    /**
     * 요구사항 1.4 — 단순 증가. 같은 사람의 중복 카운트를 막지 않는다(기획에 없어 그렇게 정했다).
     *
     * <p>⚠️ 이 메서드는 <b>읽은 값에 1을 더해 쓴다</b>. 동시에 두 요청이 들어오면 하나가 묻힌다.
     * 조회수는 그 정도의 오차가 문제되지 않는 값이라 여기서 잠금을 걸지 않는다 —
     * 잠금을 걸면 상세 조회가 직렬화되어 <b>읽기 성능을 조회수 때문에 잃는다</b>.
     * 정확한 값이 필요해지면 {@code PostRepository.increaseViewCount} 의 UPDATE 를 쓴다.
     */
    public void increaseViewCount() {
        this.viewCount++;
    }

    /** 요구사항 1.3 — 화면에서 버튼을 숨기는 것은 검증이 아니다. 판정은 항상 서버의 이 값으로 한다. */
    public boolean isOwnedBy(Long userId) {
        return userId != null && author != null && userId.equals(author.getId());
    }
}
