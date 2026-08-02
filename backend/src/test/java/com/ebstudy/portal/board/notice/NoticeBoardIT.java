package com.ebstudy.portal.board.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.BoardSearchCriteria;
import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import com.ebstudy.portal.support.IntegrationTestBase;
import com.ebstudy.portal.user.Role;
import com.ebstudy.portal.user.User;
import com.ebstudy.portal.user.UserRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 공지사항(003) 통합 검증 — 요구사항 3장.
 *
 * <p>HTTP 가 아니라 <b>서비스</b>를 직접 부른다. 이유: 요구사항 1.3 이 요구하는
 * "목록·상세는 누구나" 를 위해서는 {@code auth/SecurityConfig} 에
 * {@code GET /api/notices/**} {@code permitAll} 이 필요한데 그 파일은 이 담당의
 * 경계 밖이라 아직 열려 있지 않다. 그 한 줄이 없다고 게시판 로직 검증을 통째로 미루지 않는다 —
 * 경로 보호는 {@code AuthorizationIT} 가 이미 보는 것이고, 여기서 볼 것은
 * <b>고정 글과 페이징의 상호작용</b>·번호·조회수·권한이다.
 *
 * <p>실제 PostgreSQL 에 붙으므로 V5~V10 마이그레이션과 CHECK 제약도 함께 지나간다.
 */
class NoticeBoardIT extends IntegrationTestBase {

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private NoticeAdminService noticeAdminService;

    @Autowired
    private UserRepository users;

    private AuthenticatedUser admin;
    private AuthenticatedUser member;
    private Long categoryId;

    @BeforeEach
    void prepare() {
        // 부모의 resetState() 가 users 를 TRUNCATE CASCADE 하므로 posts 도 함께 비워진다.
        // categories 는 users 를 참조하지 않아 남는다 → V10 기본 분류를 그대로 쓴다
        admin = principalOf("noticeadm", "홍관리", Role.ADMIN);
        member = principalOf("noticeusr", "김사용", Role.USER);
        categoryId = noticeService.activeCategories().getFirst().id();
    }

    @Test
    @DisplayName("요구사항 3.1 — 고정 글은 페이징 밖에서 최대 5개, 일반 글 페이징은 그대로다")
    void pinnedIsSeparateFromPaging() {
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 1; i <= 7; i++) {
            create("고정 " + i, true, now);
        }
        for (int i = 1; i <= 12; i++) {
            create("일반 " + i, false, now);
        }

        NoticeListResponse first = noticeService.list(criteria(0, 10), now);

        // 7개를 고정해도 사용자에게는 최신 5개만 — 요구사항 3.1
        assertThat(first.pinned()).hasSize(NoticeService.PINNED_LIMIT);
        assertThat(first.pinned()).allMatch(NoticeListItem::pinned);
        // 요구사항 3.1 "번호 대신 분류명(알림)" → 서버가 번호를 아예 주지 않는다
        assertThat(first.pinned()).allMatch(item -> item.displayNumber() == null);

        // 일반 목록에 고정 글이 섞이지 않는다. "10개씩 보기" 가 그대로 10건이다
        assertThat(first.page().items()).hasSize(10);
        assertThat(first.page().items()).noneMatch(NoticeListItem::pinned);
        assertThat(first.page().totalElements()).isEqualTo(12);
        assertThat(first.page().totalPages()).isEqualTo(2);
        // 요구사항 1.1 "전체 게시글 수 기준 역순"
        assertThat(first.page().items().getFirst().displayNumber()).isEqualTo(12);
        assertThat(first.page().items().getLast().displayNumber()).isEqualTo(3);

        NoticeListResponse second = noticeService.list(criteria(1, 10), now);

        // ★ 고정 글은 2페이지에도 똑같이 온다 — 요구사항 3.1 "모든 페이지의 제일 상단"
        assertThat(second.pinned()).hasSize(NoticeService.PINNED_LIMIT);
        assertThat(second.page().items()).hasSize(2);
        assertThat(second.page().items().getFirst().displayNumber()).isEqualTo(2);
        assertThat(second.page().items().getLast().displayNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("요구사항 3.3 — 관리 목록은 고정 글을 섞어 보여주고 실제 고정 개수를 알려준다")
    void adminListShowsEveryPinnedPost() {
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 1; i <= 7; i++) {
            create("고정 " + i, true, now);
        }
        create("일반 1", false, now);

        NoticeAdminListResponse response = noticeAdminService.list(criteria(0, 50), admin, now);

        // 사용자 화면에는 5개만 보이지만 관리자는 6·7번째도 찾아서 고정을 풀 수 있어야 한다
        assertThat(response.page().totalElements()).isEqualTo(8);
        assertThat(response.page().items()).filteredOn(NoticeListItem::pinned).hasSize(7);
        assertThat(response.pinnedCount()).isEqualTo(7);
        assertThat(response.pinnedLimit()).isEqualTo(NoticeService.PINNED_LIMIT);
    }

    @Test
    @DisplayName("요구사항 1.4 — 상세 조회는 조회수를 올리고, 관리자의 수정 폼 조회는 올리지 않는다")
    void viewCountIncreasesOnlyOnUserDetail() {
        OffsetDateTime now = OffsetDateTime.now();
        Long id = create("조회수", false, now).id();

        assertThat(noticeService.read(id, null).viewCount()).isEqualTo(1);
        assertThat(noticeService.read(id, null).viewCount()).isEqualTo(2);
        // 운영 행위로 조회수가 오염되지 않는다
        assertThat(noticeAdminService.read(id, admin).viewCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("요구사항 3.3 — 고정 해제와 삭제가 사용자 목록에 그대로 반영된다")
    void unpinAndDelete() {
        OffsetDateTime now = OffsetDateTime.now();
        Long id = create("고정", true, now).id();

        assertThat(noticeService.list(criteria(0, 10), now).pinned()).hasSize(1);

        noticeAdminService.update(id,
                new NoticeWriteRequest(categoryId, "고정 해제", "본문", false), admin, now);
        NoticeListResponse afterUnpin = noticeService.list(criteria(0, 10), now);
        assertThat(afterUnpin.pinned()).isEmpty();
        assertThat(afterUnpin.page().totalElements()).isEqualTo(1);

        noticeAdminService.delete(id, admin);
        assertThat(noticeService.list(criteria(0, 10), now).page().totalElements()).isZero();
    }

    @Test
    @DisplayName("요구사항 0장·1.3 — 공지 등록·수정·삭제는 관리자만. 화면 숨김이 아니라 서버가 막는다")
    void onlyAdminCanWrite() {
        OffsetDateTime now = OffsetDateTime.now();
        NoticeWriteRequest request = new NoticeWriteRequest(categoryId, "제목", "본문", false);

        assertThatThrownBy(() -> noticeAdminService.create(request, member, now))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.AUTH_FORBIDDEN);

        assertThatThrownBy(() -> noticeAdminService.create(request, null, now))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.AUTH_REQUIRED);

        Long id = create("관리자 글", false, now).id();
        assertThatThrownBy(() -> noticeAdminService.delete(id, member))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("요구사항 1.2 — 제목 100자 미만 · 내용 4000자 미만을 서버가 검증한다")
    void lengthIsValidatedOnServer() {
        OffsetDateTime now = OffsetDateTime.now();
        String title100 = "가".repeat(100);
        String content4000 = "나".repeat(4000);

        assertThatThrownBy(() -> noticeAdminService.create(
                new NoticeWriteRequest(categoryId, title100, "본문", false), admin, now))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> noticeAdminService.create(
                new NoticeWriteRequest(categoryId, "제목", content4000, false), admin, now))
                .isInstanceOf(ApiException.class);
        // 공백만 있는 제목은 "제목 없는 줄" 을 만든다 → 통과시키지 않는다
        assertThatThrownBy(() -> noticeAdminService.create(
                new NoticeWriteRequest(categoryId, "   ", "본문", false), admin, now))
                .isInstanceOf(ApiException.class);
        // 분류는 필수다(요구사항 3.3)
        assertThatThrownBy(() -> noticeAdminService.create(
                new NoticeWriteRequest(null, "제목", "본문", false), admin, now))
                .isInstanceOf(ApiException.class);

        // 경계 바로 아래는 통과한다
        assertThat(noticeAdminService.create(
                new NoticeWriteRequest(categoryId, "가".repeat(99), "나".repeat(3999), false),
                admin, now).id()).isNotNull();
    }

    @Test
    @DisplayName("요구사항 1.1 — 검색어는 제목·내용에 부분 일치하고 등록자는 보지 않는다(공지 전용 규칙)")
    void keywordMatchesTitleAndContentOnly() {
        OffsetDateTime now = OffsetDateTime.now();
        noticeAdminService.create(new NoticeWriteRequest(categoryId, "점검 안내", "본문", false),
                admin, now);
        noticeAdminService.create(new NoticeWriteRequest(categoryId, "다른 글", "점검 내용", false),
                admin, now);
        noticeAdminService.create(new NoticeWriteRequest(categoryId, "무관", "무관", false),
                admin, now);

        assertThat(search("점검", now).page().totalElements()).isEqualTo(2);
        // 등록자 이름("홍관리")으로는 찾히지 않는다 — 요구사항 0장 "공지사항 검색 범위: 제목·내용"
        assertThat(search("홍관리", now).page().totalElements()).isZero();
        // LIKE 메타문자는 이스케이프된다 — "%" 하나로 전체를 훑을 수 없다
        assertThat(search("%", now).page().totalElements()).isZero();
    }

    @Test
    @DisplayName("요구사항 1.1 — 기간 1년 초과·허용하지 않는 개씩보기·모르는 정렬은 거부한다")
    void searchConditionsAreValidated() {
        OffsetDateTime now = OffsetDateTime.now();

        assertThatThrownBy(() -> NoticeRequestParams.toCriteria(
                now.minusYears(2).toString(), now.toString(), null, null, null, null, null, null,
                now)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> NoticeRequestParams.toCriteria(
                null, null, null, null, null, "15", null, null, now))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> NoticeRequestParams.toCriteria(
                null, null, null, null, null, null, "title;DROP TABLE posts--", null, now))
                .isInstanceOf(ApiException.class);
        // 숫자가 아닌 page 는 500 이 아니라 400 이어야 한다
        assertThatThrownBy(() -> NoticeRequestParams.toCriteria(
                null, null, null, null, "abc", null, null, null, now))
                .isInstanceOf(ApiException.class);

        // 날짜만 온 to 는 그날의 끝으로 본다 — 오늘 쓴 글이 검색에서 빠지지 않는다
        create("오늘 글", false, now);
        BoardSearchCriteria today = NoticeRequestParams.toCriteria(
                now.toLocalDate().toString(), now.toLocalDate().toString(), null, null, null, null,
                null, null, now);
        assertThat(noticeService.list(today, now).page().totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 게시판 글 id 로 공지 상세에 들어올 수 없다")
    void cannotReachOtherBoardPost() {
        assertThatThrownBy(() -> noticeService.read(999_999L, null))
                .isInstanceOf(ApiException.class);
    }

    // ── 도우미 ──────────────────────────────────────────────

    private NoticeDetailResponse create(String title, boolean pinned, OffsetDateTime now) {
        return noticeAdminService.create(
                new NoticeWriteRequest(categoryId, title, title + " 본문", pinned), admin, now);
    }

    private BoardSearchCriteria criteria(int page, int size) {
        return BoardSearchCriteria.of(BoardType.NOTICE, null, null, null, null, false, null,
                page, size, null, null, OffsetDateTime.now());
    }

    private NoticeListResponse search(String keyword, OffsetDateTime now) {
        return noticeService.list(NoticeRequestParams.toCriteria(null, null, null, keyword, null,
                null, null, null, now), now);
    }

    private AuthenticatedUser principalOf(String username, String name, Role role) {
        User saved = users.save(User.create(username, "{noop}unused", name, role,
                OffsetDateTime.now()));
        return new AuthenticatedUser(saved.getId(), username, role);
    }
}
