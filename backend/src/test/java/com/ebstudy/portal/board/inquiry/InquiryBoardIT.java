package com.ebstudy.portal.board.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.PageResponse;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import com.ebstudy.portal.support.IntegrationTestBase;
import com.ebstudy.portal.user.Role;
import com.ebstudy.portal.user.User;
import com.ebstudy.portal.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 문의게시판(006) 통합 검증 — 요구사항 6장 · {@code specs/006-inquiry/spec.md}.
 *
 * <p>HTTP 가 아니라 <b>서비스</b>를 직접 부른다({@code NoticeBoardIT} 와 같은 이유):
 * 요구사항 1.3 / FR-001 이 요구하는 "목록·상세는 누구나" 를 위해서는
 * {@code auth/SecurityConfig} 에 {@code GET /api/inquiries/**} {@code permitAll} 이 필요한데
 * 그 파일은 이 담당의 경계 밖이라 아직 열려 있지 않다.
 *
 * <p>★ 서비스를 <b>트랜잭션 없이</b> 부르는 것 자체가 검증 항목이다 —
 * 조회 경로가 {@code @EntityGraph} 로 엔티티를 완성해 돌려주지 못하면
 * 여기서 {@code LazyInitializationException} 이 난다({@code open-in-view=false}).
 * 그 성질이 있어야 무차별 대입 방어의 지연을 트랜잭션 밖에 둘 수 있다.
 */
class InquiryBoardIT extends IntegrationTestBase {

    @Autowired
    private InquiryService inquiries;

    @Autowired
    private InquiryAnswerService answers;

    @Autowired
    private SecretPasswordAttemptService attempts;

    @Autowired
    private SecretReadGrantService grants;

    @Autowired
    private UserRepository users;

    private AuthenticatedUser author;
    private AuthenticatedUser stranger;
    private AuthenticatedUser admin;

    private static final String CLIENT = "203.0.113.7";

    @BeforeEach
    void prepare() {
        attempts.clearAll();
        grants.clearAll();
        author = principalOf("inqauthor", "김작성", Role.USER);
        stranger = principalOf("inqother", "박남", Role.USER);
        admin = principalOf("inqadmin", "홍관리", Role.ADMIN);
    }

    // ── US1 목록 ─────────────────────────────────────────────

    @Test
    @DisplayName("AC-1 · AC-15 — 비로그인도 목록을 보고, 응답에 내용·답변 필드가 아예 없다")
    void anonymousCanListAndListNeverCarriesContent() {
        create(author, "배송 문의", "내용 공개", false, null);
        create(author, "비밀 문의", "내용 비밀", true, "1234");

        PageResponse<InquiryListItemResponse> page = list(null, null);

        assertThat(page.items()).hasSize(2);
        // 비밀글도 제목과 등록자가 보인다 — AC-22 · 판단 14
        assertThat(page.items()).extracting(InquiryListItemResponse::title)
                .containsExactlyInAnyOrder("배송 문의", "비밀 문의");
        assertThat(page.items()).extracting(InquiryListItemResponse::authorName)
                .containsOnly("김작성");
        // 내용을 담을 자리가 타입에 없다 — 화면이 안 그리는 것과 서버가 안 보내는 것은 다르다
        assertThat(InquiryListItemResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("content", "answer", "answerContent");
    }

    @Test
    @DisplayName("★ AC-5 · AC-6 — 비밀글의 내용은 제3자 검색에 안 걸리고 본인·관리자에게는 걸린다")
    void secretContentIsNotSearchableByOthers() {
        create(author, "결제 오류", "환불계좌 알려드립니다", true, "1234");

        // 제3자(비로그인) — 내용에만 있는 문자열로는 찾을 수 없다.
        // 걸리면 검색 결과의 유무로 내용을 한 글자씩 복원할 수 있다
        assertThat(list(null, "환불계좌").items()).isEmpty();
        assertThat(list(stranger, "환불계좌").items()).isEmpty();

        // 본인과 관리자는 어차피 본문을 볼 수 있으므로 빼면 기능만 불편해진다
        assertThat(list(author, "환불계좌").items()).hasSize(1);
        assertThat(list(admin, "환불계좌").items()).hasSize(1);

        // 제목·등록자는 비밀글이어도 누구에게나 검색된다 — 목록에 이미 보이는 값이다
        assertThat(list(null, "결제").items()).hasSize(1);
        assertThat(list(null, "김작성").items()).hasSize(1);
    }

    @Test
    @DisplayName("AC-13 · AC-14 — 나의 문의내역은 AND 로 걸리고, 비로그인 요청은 401 이다")
    void mineOnlyFilter() {
        create(author, "배송 문의 내것", "내용", false, null);
        create(stranger, "배송 문의 남것", "내용", false, null);
        create(author, "다른 주제", "내용", false, null);

        PageResponse<InquiryListItemResponse> mine = inquiries.list(null, null, "배송", true,
                null, null, null, null, author);
        assertThat(mine.items()).extracting(InquiryListItemResponse::title)
                .containsExactly("배송 문의 내것");

        // 조용히 무시하고 전체를 주면 사용자는 "자기 글만 본다고 믿으면서 전체를 본다"
        assertThatThrownBy(() -> inquiries.list(null, null, null, true, null, null, null, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.AUTH_REQUIRED);
    }

    @Test
    @DisplayName("AC-10 — 글 번호는 전체 건수 기준 역순이다(행 번호가 아니다)")
    void numberingIsDescendingOverTotal() {
        for (int i = 1; i <= 25; i++) {
            create(author, "문의 " + i, "내용", false, null);
        }
        PageResponse<InquiryListItemResponse> first = inquiries.list(null, null, null, null,
                0, 10, null, null, null);
        PageResponse<InquiryListItemResponse> second = inquiries.list(null, null, null, null,
                1, 10, null, null, null);

        assertThat(first.totalElements()).isEqualTo(25);
        assertThat(first.items().getFirst().number()).isEqualTo(25);
        assertThat(second.items().getFirst().number()).isEqualTo(15);
    }

    // ── US3 비밀글 ───────────────────────────────────────────

    @Test
    @DisplayName("AC-28 · AC-30 · AC-31 — 제3자는 403, 작성자와 관리자는 비밀번호 없이 열린다")
    void secretDetailAccess() {
        Long id = create(author, "비밀 문의", "본문 비밀", true, "1234").id();

        assertThatThrownBy(() -> inquiries.detail(id, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(intendedCode((ApiException) ex))
                        .isEqualTo("SECRET_POST_LOCKED"));
        // 로그인했더라도 남의 비밀글이면 같은 잠금이다 — 403 · SECRET_POST_LOCKED
        ApiException locked = (ApiException) catchOf(() -> inquiries.detail(id, null, stranger));
        assertThat(locked.code()).isEqualTo(ErrorCode.SECRET_POST_LOCKED);
        assertThat(locked.code().status().value()).isEqualTo(403);

        assertThat(inquiries.detail(id, null, author).content()).isEqualTo("본문 비밀");
        assertThat(inquiries.detail(id, null, admin).content()).isEqualTo("본문 비밀");
    }

    @Test
    @DisplayName("AC-29 · AC-37 — 맞히면 열리고, 그 통과는 그 글에만 적용된다")
    void unlockGrantsReadForThatPostOnly() {
        Long a = create(author, "비밀 A", "본문 A", true, "1234").id();
        Long b = create(author, "비밀 B", "본문 B", true, "5678").id();

        InquiryUnlockResponse opened = inquiries.unlock(a, "1234", null, CLIENT);
        assertThat(opened.inquiry().content()).isEqualTo("본문 A");
        assertThat(opened.grantToken()).isNotBlank();

        // A 는 다시 묻지 않는다
        assertThat(inquiries.detail(a, opened.grantToken(), null).content()).isEqualTo("본문 A");
        // ★ A 의 통과가 B 로 번지면 한 글만 뚫려도 전부 열린다
        assertThatThrownBy(() -> inquiries.detail(b, opened.grantToken(), null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("★ AC-33 — 비밀번호를 맞혀도 수정·삭제는 못 한다(열람만 얻는다)")
    void passwordGrantsReadOnly() {
        Long id = create(author, "비밀 문의", "본문", true, "1234").id();
        inquiries.unlock(id, "1234", stranger, CLIENT);

        assertThat(((ApiException) catchOf(() -> inquiries.update(id,
                new InquiryUpdateRequest("고침", "고침", false, null), stranger))).code())
                .isEqualTo(ErrorCode.AUTH_FORBIDDEN);
        assertThat(((ApiException) catchOf(() -> inquiries.delete(id, stranger))).code())
                .isEqualTo(ErrorCode.AUTH_FORBIDDEN);
    }

    @Test
    @DisplayName("★ AC-38 — 없는 글(404 의도)과 틀린 비밀번호(403)를 다르게 응답한다")
    void missingPostAndWrongPasswordDiffer() {
        Long id = create(author, "비밀 문의", "본문", true, "1234").id();

        assertThat(intendedCode((ApiException) catchOf(() -> inquiries.detail(999_999L, null, null))))
                .isEqualTo("POST_NOT_FOUND");

        ApiException wrong = (ApiException) catchOf(() -> inquiries.unlock(id, "9999", null, CLIENT));
        assertThat(intendedCode(wrong)).isEqualTo("SECRET_PASSWORD_MISMATCH");
        // AC-39 — 401 이 아니다. 401 이면 글 비밀번호를 한 번 틀렸다고 로그아웃된다
        assertThat(wrong.code().status().value()).isEqualTo(403);
        // 판단 9 — 남은 시도 횟수를 알려 준다
        assertThat(wrong.errors()).anySatisfy(
                e -> assertThat(e.field()).isEqualTo("remainingAttempts"));
    }

    @Test
    @DisplayName("★ AC-34 · AC-36 — 반복 실패는 429 로 막히고, 그 차단이 작성자·관리자를 막지 않는다")
    void bruteForceIsBlockedButOwnerIsNot() {
        Long id = create(author, "비밀 문의", "본문", true, "1234").id();

        for (int i = 1; i <= 10; i++) {
            assertThat(intendedCode((ApiException) catchOf(
                    () -> inquiries.unlock(id, "0000", null, CLIENT))))
                    .isEqualTo("SECRET_PASSWORD_MISMATCH");
        }
        ApiException blocked = (ApiException) catchOf(() -> inquiries.unlock(id, "1234", null, CLIENT));
        assertThat(intendedCode(blocked)).isEqualTo("SECRET_PASSWORD_TOO_MANY_ATTEMPTS");
        assertThat(blocked.code().status().value()).isEqualTo(429);

        // ★ 차단은 비밀번호 입력 경로에만 걸린다 — 주인과 관리자가 쫓겨나지 않는다
        assertThat(inquiries.detail(id, null, author).content()).isEqualTo("본문");
        assertThat(inquiries.detail(id, null, admin).content()).isEqualTo("본문");
    }

    @Test
    @DisplayName("AC-42 — 조회수는 열람에 성공했을 때만 오른다")
    void viewCountOnlyOnSuccess() {
        Long id = create(author, "비밀 문의", "본문", true, "1234").id();

        for (int i = 0; i < 3; i++) {
            catchOf(() -> inquiries.unlock(id, "0000", null, CLIENT));
        }
        catchOf(() -> inquiries.detail(id, null, stranger));
        assertThat(inquiries.preview(id).viewCount()).isZero();

        inquiries.unlock(id, "1234", null, CLIENT);
        assertThat(inquiries.preview(id).viewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-23 · AC-24 · AC-25 — 비밀번호는 필수·숫자 4자리이고 평문으로 저장되지 않는다")
    void secretPasswordRules() {
        assertThat(intendedCode((ApiException) catchOf(
                () -> create(author, "제목", "내용", true, null))))
                .isEqualTo("SECRET_PASSWORD_REQUIRED");
        assertThat(intendedCode((ApiException) catchOf(
                () -> create(author, "제목", "내용", true, "12a4"))))
                .isEqualTo("SECRET_PASSWORD_FORMAT_INVALID");

        Long one = create(author, "제목1", "내용", true, "1234").id();
        Long two = create(author, "제목2", "내용", true, "1234").id();
        List<String> hashes = jdbcTemplate.queryForList(
                "select secret_password_hash from posts where id in (?, ?)", String.class, one, two);
        assertThat(hashes).noneMatch(h -> h.contains("1234"));
        // 글마다 소금값이 달라야 "비밀번호가 같은 글" 을 저장값 비교만으로 묶을 수 없다
        assertThat(hashes.get(0)).isNotEqualTo(hashes.get(1));
    }

    @Test
    @DisplayName("AC-26 — 비밀글을 해제하면 공개되고 저장된 비밀번호가 남지 않는다")
    void unsettingSecretClearsPassword() {
        Long id = create(author, "비밀 문의", "본문", true, "1234").id();
        inquiries.update(id, new InquiryUpdateRequest("공개 전환", "본문", false, null), author);

        assertThat(inquiries.detail(id, null, null).content()).isEqualTo("본문");
        assertThat(jdbcTemplate.queryForObject(
                "select secret_password_hash from posts where id = ?", String.class, id)).isNull();
    }

    // ── US2 · US4 답변과 권한 ────────────────────────────────

    @Test
    @DisplayName("AC-18 — 제목·내용의 필수와 길이를 서버가 막는다(수정도 같다)")
    void titleAndContentValidatedOnServer() {
        assertThat(intendedCode((ApiException) catchOf(() -> create(author, "  ", "내용", false, null))))
                .isEqualTo("POST_TITLE_REQUIRED");
        assertThat(intendedCode((ApiException) catchOf(
                () -> create(author, "가".repeat(100), "내용", false, null))))
                .isEqualTo("POST_TITLE_LENGTH_INVALID");
        assertThat(create(author, "가".repeat(99), "내용", false, null).id()).isNotNull();

        assertThat(intendedCode((ApiException) catchOf(() -> create(author, "제목", "", false, null))))
                .isEqualTo("POST_CONTENT_REQUIRED");
        assertThat(intendedCode((ApiException) catchOf(
                () -> create(author, "제목", "가".repeat(4000), false, null))))
                .isEqualTo("POST_CONTENT_LENGTH_INVALID");

        Long id = create(author, "제목", "내용", false, null).id();
        assertThat(intendedCode((ApiException) catchOf(() -> inquiries.update(id,
                new InquiryUpdateRequest("", "내용", false, null), author))))
                .isEqualTo("POST_TITLE_REQUIRED");
    }

    @Test
    @DisplayName("오류 규약 — 코드는 응답의 code 하나로만 읽는다(errors 는 곁들이는 값 전용)")
    void codeIsCarriedByTheCodeFieldAlone() {
        // 002 통합 전에는 ErrorCode 에 게시판 코드가 없어 errors[0].field 에 "의도한 코드"를
        // 실어 보내는 임시 규약을 썼다. 이제 진짜 코드가 생겼으므로 화면은 code 만 본다.
        // errors 는 코드로 표현할 수 없는 값(남은 시도 횟수)이 있을 때만 붙는다
        ApiException state = (ApiException) catchOf(() -> inquiries.detail(999_999L, null, null));
        assertThat(state.code()).isEqualTo(ErrorCode.POST_NOT_FOUND);
        assertThat(state.code().status().value()).isEqualTo(404);
        assertThat(state.errors()).isEmpty();

        ApiException input = (ApiException) catchOf(() -> create(author, "", "내용", false, null));
        assertThat(input.code()).isEqualTo(ErrorCode.POST_TITLE_REQUIRED);
        assertThat(input.errors()).isEmpty();

        // 유일한 예외 — 남은 시도 횟수는 코드에 담을 수 없다
        Long secret = create(author, "비밀 문의", "본문", true, "1234").id();
        ApiException wrong = (ApiException) catchOf(() -> inquiries.unlock(secret, "9999", null, CLIENT));
        assertThat(wrong.code()).isEqualTo(ErrorCode.SECRET_PASSWORD_MISMATCH);
        assertThat(wrong.errors()).singleElement()
                .satisfies(e -> assertThat(e.field()).isEqualTo("remainingAttempts"));
    }

    @Test
    @DisplayName("AC-19 — 남의 글은 수정·삭제할 수 없다. 관리자도 수정은 못 한다(판단 16)")
    void onlyOwnerCanModify() {
        Long id = create(author, "제목", "내용", false, null).id();
        InquiryUpdateRequest edit = new InquiryUpdateRequest("고침", "고침", false, null);

        assertThat(((ApiException) catchOf(() -> inquiries.update(id, edit, stranger))).code())
                .isEqualTo(ErrorCode.AUTH_FORBIDDEN);
        assertThat(((ApiException) catchOf(() -> inquiries.update(id, edit, admin))).code())
                .isEqualTo(ErrorCode.AUTH_FORBIDDEN);
        // 관리자에게 주는 것은 열람·답변·삭제다
        inquiries.deleteByAdmin(id, admin);
        assertThat(list(null, null).items()).isEmpty();
    }

    @Test
    @DisplayName("AC-20 · AC-21 · AC-43 · AC-45 — 답변이 달리면 작성자는 수정·삭제할 수 없고 답변은 하나다")
    void answerLocksTheQuestion() {
        Long id = create(author, "제목", "내용", false, null).id();
        assertThat(inquiries.detail(id, null, author).editable()).isTrue();

        answers.create(id, "답변 드립니다", admin);
        assertThat(inquiries.detail(id, null, author).answered()).isTrue();
        assertThat(inquiries.detail(id, null, author).editable()).isFalse();
        assertThat(inquiries.detail(id, null, author).answer().adminName()).isEqualTo("홍관리");

        assertThat(intendedCode((ApiException) catchOf(() -> inquiries.update(id,
                new InquiryUpdateRequest("고침", "고침", false, null), author))))
                .isEqualTo("INQUIRY_ALREADY_ANSWERED");
        assertThat(intendedCode((ApiException) catchOf(() -> inquiries.delete(id, author))))
                .isEqualTo("INQUIRY_ALREADY_ANSWERED");

        // 문의당 답변은 하나다
        assertThat(intendedCode((ApiException) catchOf(() -> answers.create(id, "또 답변", admin))))
                .isEqualTo("INQUIRY_ANSWER_ALREADY_EXISTS");
        // 고칠 수는 있고 상태는 답변완료 그대로다
        assertThat(answers.update(id, "고친 답변", admin).content()).isEqualTo("고친 답변");
        assertThat(inquiries.detail(id, null, author).answered()).isTrue();
    }

    @Test
    @DisplayName("AC-44 · AC-46 — 일반 사용자는 답변할 수 없고 답변 내용도 서버가 검증한다")
    void onlyAdminCanAnswer() {
        Long id = create(author, "제목", "내용", false, null).id();

        assertThat(((ApiException) catchOf(() -> answers.create(id, "답변", author))).code())
                .isEqualTo(ErrorCode.AUTH_FORBIDDEN);
        assertThat(intendedCode((ApiException) catchOf(() -> answers.create(id, "  ", admin))))
                .isEqualTo("INQUIRY_ANSWER_CONTENT_REQUIRED");
        assertThat(intendedCode((ApiException) catchOf(
                () -> answers.create(id, "가".repeat(4000), admin))))
                .isEqualTo("INQUIRY_ANSWER_CONTENT_LENGTH_INVALID");
        assertThat(inquiries.detail(id, null, author).answered()).isFalse();
    }

    @Test
    @DisplayName("★ AC-41 — 비밀글의 답변도 본문과 똑같이 보호된다(유무만 보인다)")
    void secretAnswerIsProtectedToo() {
        Long id = create(author, "비밀 문의", "본문", true, "1234").id();
        answers.create(id, "답변에 질문이 인용된다", admin);

        assertThatThrownBy(() -> inquiries.detail(id, null, null)).isInstanceOf(ApiException.class);
        InquiryDetailResponse locked = inquiries.preview(id);
        assertThat(locked.answered()).isTrue();
        assertThat(locked.answer()).isNull();
        assertThat(locked.content()).isNull();
    }

    // ── 도우미 ───────────────────────────────────────────────

    private PageResponse<InquiryListItemResponse> list(AuthenticatedUser principal, String keyword) {
        return inquiries.list(null, null, keyword, null, null, null, null, null, principal);
    }

    private InquiryDetailResponse create(AuthenticatedUser principal, String title, String content,
            boolean secret, String password) {
        return inquiries.create(new InquiryCreateRequest(title, content, secret, password),
                principal);
    }

    /**
     * 002 통합 이후 코드는 {@code code} 하나에만 있다.
     *
     * <p>예전에는 {@code common/ErrorCode} 가 잠겨 있어 {@code errors[0].field} 에 "의도한
     * 코드"를 실어 보냈고 이 도우미가 그쪽을 먼저 봤다. 이제 {@code errors} 는 코드로 표현할
     * 수 없는 값(남은 시도 횟수)만 담으므로 그쪽을 보면 코드 대신 그 값이 잡힌다.
     */
    private static String intendedCode(ApiException ex) {
        return ex.code().name();
    }

    private static Throwable catchOf(Runnable action) {
        try {
            action.run();
            throw new AssertionError("예외가 나야 한다");
        } catch (AssertionError error) {
            throw error;
        } catch (Throwable thrown) {
            return thrown;
        }
    }

    private AuthenticatedUser principalOf(String username, String name, Role role) {
        User saved = users.save(User.create(username, "{noop}unused", name, role,
                OffsetDateTime.now()));
        return new AuthenticatedUser(saved.getId(), saved.getUsername(), saved.getRole());
    }
}
