package com.ebstudy.portal.board.inquiry;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.BoardAccessGuard;
import com.ebstudy.portal.board.common.BoardSearchCriteria;
import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.InquiryAnswerRepository;
import com.ebstudy.portal.board.common.NewBadgePolicy;
import com.ebstudy.portal.board.common.PageResponse;
import com.ebstudy.portal.board.common.Post;
import com.ebstudy.portal.board.common.PostNumbering;
import com.ebstudy.portal.board.common.SecretPostPassword;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import com.ebstudy.portal.user.User;
import com.ebstudy.portal.user.UserRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문의게시판(006) — 목록 · 상세 · 비밀글 · 등록/수정/삭제.
 *
 * <h2>이 클래스가 지키는 것</h2>
 * <ol>
 *   <li><b>목록·상세는 누구나</b>(FR-001 · AC-1). 001 {@code FR-021} 의 이행 지점이다 —
 *       여기가 막히면 그 요구사항은 프로젝트 전체에서 아무도 검증하지 않는 것이 된다</li>
 *   <li><b>비밀글의 본문과 답변은 작성자 · 관리자 · 비밀번호를 통과한 사람에게만</b>(FR-019).
 *       목록 응답 · 상세 응답 · <b>검색 결과</b> 어디에서도 새지 않는다</li>
 *   <li><b>수정·삭제의 기준은 소유자 확인 하나뿐</b>(FR-022 · AC-33). 비밀번호를 맞혀도
 *       열람만 얻는다</li>
 * </ol>
 *
 * <h2>왜 클래스에 {@code @Transactional} 을 붙이지 않았는가</h2>
 * 조회 경로는 트랜잭션을 <b>열지 않는다</b>. {@link InquiryPostRepository} 가
 * {@code @EntityGraph} 로 엔티티를 완성해 돌려주므로 지연 로딩이 없고, 그래서
 * 무차별 대입 방어의 <b>지연이 트랜잭션 밖</b>에 놓인다(트랜잭션 안에서 자면 DB 커넥션과
 * 잠금을 붙잡는다 — {@code Delayer} 주석). 쓰기 메서드에만 {@code @Transactional} 을 붙이고,
 * 조회수 증가는 {@link InquiryViewCounter} 가 자기 트랜잭션에서 한다.
 */
@Service
@RequiredArgsConstructor
public class InquiryService {

    private static final BoardType BOARD = BoardType.INQUIRY;
    /** 메인 페이지(요구사항 2장)가 5개를 쓴다. 상한을 두지 않으면 목록 API 를 우회하는 통로가 된다. */
    private static final int LATEST_MAX = 20;

    private final InquiryPostRepository posts;
    private final InquiryAnswerViewRepository answerViews;
    private final InquiryAnswerRepository answers;
    private final UserRepository users;
    private final BoardAccessGuard guard;
    private final SecretPostPassword secretPassword;
    private final SecretPasswordAttemptService attempts;
    private final SecretReadGrantService grants;
    private final InquiryViewCounter viewCounter;
    private final NewBadgePolicy newBadge;

    // ── 목록 ─────────────────────────────────────────────────

    /**
     * 요구사항 6.1 목록. <b>인증을 요구하지 않는다</b>(FR-001 · AC-1).
     *
     * @param mineRequested "나의 문의내역만 보기". ★ 미로그인이면 <b>401 로 거부</b>한다(AC-14).
     *                      조건을 조용히 무시하고 전체를 주면 사용자는 <b>자기 글만 본다고
     *                      믿으면서 전체를 보게 된다.</b> 401 이면 화면이 로그인으로 보낸 뒤
     *                      001 {@code AC-24}(원래 목적지 복귀)로 필터가 걸린 목록에 돌아온다
     */
    public PageResponse<InquiryListItemResponse> list(OffsetDateTime from, OffsetDateTime to,
            String keyword, Boolean mineRequested, Integer page, Integer size, String sort,
            String direction, AuthenticatedUser principal) {
        if (Boolean.TRUE.equals(mineRequested)) {
            guard.requireLogin(principal);
        }
        return search(from, to, keyword, mineRequested, page, size, sort, direction, principal);
    }

    /**
     * 요구사항 6.5 관리자 목록. "나의 문의내역" 은 관리자에게 의미가 없어 받지 않는다 —
     * 받으면 <b>관리자가 쓴 문의만</b> 보이는 화면이 되는데 그건 관리 목록이 아니다.
     */
    public PageResponse<InquiryListItemResponse> listForAdmin(OffsetDateTime from,
            OffsetDateTime to, String keyword, Integer page, Integer size, String sort,
            String direction, AuthenticatedUser principal) {
        guard.requireAdmin(principal);
        return search(from, to, keyword, false, page, size, sort, direction, principal);
    }

    private PageResponse<InquiryListItemResponse> search(OffsetDateTime from, OffsetDateTime to,
            String keyword, Boolean mineRequested, Integer page, Integer size, String sort,
            String direction, AuthenticatedUser principal) {
        OffsetDateTime now = OffsetDateTime.now();
        Long viewerId = principal == null ? null : principal.userId();
        // 검증은 여기 하나뿐이다 — 기간(기본 1달·최대 1년) · 개씩 보기 · 정렬 화이트리스트 ·
        // 문의게시판에 분류 조건/분류 정렬 금지가 전부 이 안에서 판정된다(공통 기반)
        BoardSearchCriteria criteria = BoardSearchCriteria.of(BOARD, from, to, null, keyword,
                mineRequested, viewerId, page, size, sort, direction, now);

        Page<Post> found = posts.findAll(
                InquirySpecifications.search(criteria, viewerId, guard.isAdmin(principal)),
                criteria.toPageable());

        return PageResponse.of(found, toListItems(found, principal, now));
    }

    /** 메인 페이지(요구사항 2장) — 최신 N개 + 답변완료 표기 + 자물쇠. */
    public List<InquiryListItemResponse> latest(Integer limit, AuthenticatedUser principal) {
        int size = Math.clamp(limit == null ? 5 : limit, 1, LATEST_MAX);
        OffsetDateTime now = OffsetDateTime.now();
        List<Post> found = posts.findByBoardTypeOrderByCreatedAtDescIdDesc(BOARD,
                PageRequest.of(0, size));
        // 최신 N개는 "게시판 전체를 위에서 N개 자른 것" 이므로 번호의 기준도 게시판 전체 수다
        return toItems(found, posts.countByBoardType(BOARD), 0, size, principal, now);
    }

    private List<InquiryListItemResponse> toListItems(Page<Post> found,
            AuthenticatedUser principal, OffsetDateTime now) {
        // ★ 글 번호의 기준을 "현재 검색 결과의 전체 수" 로 둔다.
        //   요구사항 1.1 은 "전체 게시글 수" 라고만 적어 게시판 전체인지 검색 결과인지 단정하지
        //   않는다(공통 기반 보고서 E-4 가 남긴 그 선택이다). 검색 결과 수를 쓰면 목록이 언제나
        //   N..1 로 이어져 화면의 페이징과 어긋나지 않는다. 게시판 전체 수를 쓰면 12건짜리
        //   검색 결과의 첫 행이 873번이 되어 사용자가 번호를 신뢰할 수 없다.
        //   ⚠️ 게시판 4종이 같은 쪽으로 통일해야 한다 — 보고서 항목
        return toItems(found.getContent(), found.getTotalElements(), found.getNumber(),
                found.getSize(), principal, now);
    }

    private List<InquiryListItemResponse> toItems(List<Post> found, long totalElements, int page,
            int size, AuthenticatedUser principal, OffsetDateTime now) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<Long> ids = found.stream().map(Post::getId).toList();
        // 행마다 existsByPostId 를 부르면 N+1 이다 — 한 번에 받는다
        Set<Long> answered = new HashSet<>(answers.findAnsweredPostIds(ids));
        long[] numbers = PostNumbering.displayNumbers(totalElements, page, size, found.size());

        List<InquiryListItemResponse> items = new ArrayList<>(found.size());
        for (int i = 0; i < found.size(); i++) {
            Post post = found.get(i);
            items.add(new InquiryListItemResponse(
                    numbers[i],
                    post.getId(),
                    post.getTitle(),
                    post.isSecret(),
                    answered.contains(post.getId()),
                    // new 판정은 서버가 한다 — 화면에서 계산하면 사용자 기기의 시계가 기준이 된다
                    newBadge.isNew(BOARD, post.getCreatedAt(), now),
                    post.getViewCount(),
                    post.getCreatedAt(),
                    post.getAuthor().getName(),
                    guard.isOwner(principal, post.getAuthor().getId())));
        }
        return items;
    }

    // ── 상세 ─────────────────────────────────────────────────

    /**
     * 요구사항 6.3 상세. 인증 불필요.
     *
     * @param grantToken {@code X-Inquiry-Grant} 헤더. 앞서 잠금해제에 성공했으면 그 값이 온다
     *                   (AC-37 — 한 번 연 글은 유효 시간 동안 다시 묻지 않는다)
     */
    public InquiryDetailResponse detail(Long id, String grantToken, AuthenticatedUser principal) {
        Post post = require(id);
        if (!canRead(post, grantToken, principal)) {
            // AC-28 · AC-32 — 403. 본문도 답변도 응답에 담기지 않는다.
            // 잠금 화면에 보여줄 공개 항목은 preview 로 따로 받는다
            throw new ApiException(ErrorCode.SECRET_POST_LOCKED);
        }
        viewCounter.increase(id);
        return unlockedDetail(post, principal, post.getViewCount() + 1);
    }

    /**
     * 요구사항 6.5 관리자 상세. 관리자는 비밀번호 없이 본다(FR-020 · AC-31) —
     * 물으면 답변 기능 자체가 성립하지 않는다.
     *
     * <p>★ <b>조회수를 올리지 않는다.</b> 관리 화면의 열람은 사용자 조회 지표가 아니다.
     * 올리면 답변을 쓰려고 여러 번 드나든 흔적이 사용자에게 인기로 보인다.
     * (요구사항에 명시가 없어 정한 것 — 보고서 항목)
     */
    public InquiryDetailResponse adminDetail(Long id, AuthenticatedUser principal) {
        guard.requireAdmin(principal);
        Post post = require(id);
        return unlockedDetail(post, principal, post.getViewCount());
    }

    /**
     * 잠금 안내 — 비밀번호 입력 화면이 "어떤 글의 비밀번호를 넣는지" 를 보여주기 위한 것이다.
     * 담기는 것은 <b>목록에 이미 공개된 항목뿐</b>이라 새로 새는 정보가 없다(판단 15).
     *
     * <p>별도 엔드포인트로 뺀 이유: AC-28 이 상세를 <b>403</b> 으로 못박았는데, 이 프로젝트의
     * 오류 응답은 {@code common/ProblemDetailFactory} 가 만들고 그 형식에 임의 본문을 실을 수
     * 없다({@code common/} 은 수정 금지). 상태 코드를 200 으로 낮춰 본문을 싣는 대신,
     * <b>상태 코드는 스펙대로 두고 공개 항목만 따로 내주는</b> 쪽을 골랐다.
     */
    public InquiryDetailResponse preview(Long id) {
        Post post = require(id);
        // ★ 잠금 경로에서는 답변을 조회조차 하지 않는다 — 메모리에 올리지 않으면 샐 수 없다.
        //   유무만 필요하므로 exists 로 충분하다(FR-035 · AC-41)
        boolean answered = answers.existsByPostId(post.getId());
        return new InquiryDetailResponse(post.getId(), post.getTitle(), null, post.isSecret(),
                post.isSecret(), answered, post.getViewCount(), post.getCreatedAt(),
                post.getUpdatedAt(), post.getAuthor().getName(), false, false, false, null);
    }

    /**
     * 비밀글 잠금해제 — AC-29 · AC-34 · AC-35 · AC-38 · AC-42.
     *
     * <p>순서가 곧 방어다:
     * <ol>
     *   <li>글이 없으면 <b>404 계열</b>로 끝낸다 — 틀린 비밀번호(403)와 구분한다
     *       ({@code ErrorCode.POST_NOT_FOUND} 의 근거)</li>
     *   <li>작성자·관리자면 비밀번호를 <b>묻지 않는다</b>. 그래서 글 단위 차단이 걸려 있어도
     *       이 둘은 영향을 받지 않는다(AC-36) — 차단 확인 앞에 둔 것이 그 보장이다</li>
     *   <li>차단 확인과 지연을 <b>bcrypt 대조 앞</b>에 둔다 — 느린 해시가 CPU 소모 수단이
     *       되지 않게 한다</li>
     *   <li>성공해야만 조회수가 오른다(AC-42)</li>
     * </ol>
     */
    public InquiryUnlockResponse unlock(Long id, String rawPassword, AuthenticatedUser principal,
            String clientKey) {
        Instant now = Instant.now();
        Post post = require(id);
        if (!post.isSecret()) {
            // 비밀글이 아닌데 잠금해제를 부른 요청. 통과시키면 호출부가 비밀글 여부를 보지 않아도
            // 되는 것처럼 보이게 된다(SecretPostPassword.verify 와 같은 판단)
            throw new ApiException(ErrorCode.INQUIRY_NOT_SECRET);
        }
        if (guard.canSkipSecretPassword(principal, post.getAuthor().getId())) {
            return opened(post, principal, now);
        }

        attempts.throttle(id, clientKey, now);
        // 형식이 어긋난 입력도 "그냥 틀린 것" 으로 센다 — 형식 오류를 따로 알려 주면
        // 공격자가 시도 횟수를 소모하지 않고 입력 규칙을 확인할 수 있다
        if (!secretPassword.matches(rawPassword, post.getSecretPasswordHash())) {
            int remaining = attempts.recordFailure(id, clientKey, Instant.now());
            // 판단 9 — 남은 시도 횟수를 함께 준다. 공격자가 얻는 정보는 "제한이 있다" 뿐이고
            // 그건 429 로 어차피 드러난다. 반대로 정상 사용자는 차단 직전임을 알 수 있다
            throw new ApiException(ErrorCode.SECRET_PASSWORD_MISMATCH, List.of(
                    new ApiException.FieldError("remainingAttempts",
                            String.valueOf(Math.max(remaining, 0)))));
        }
        attempts.reset(id, clientKey);
        return opened(post, principal, now);
    }

    private InquiryUnlockResponse opened(Post post, AuthenticatedUser principal, Instant now) {
        viewCounter.increase(post.getId());
        return new InquiryUnlockResponse(grants.issue(post.getId(), now),
                unlockedDetail(post, principal, post.getViewCount() + 1));
    }

    // ── 등록 · 수정 · 삭제 ────────────────────────────────────

    /** AC-16 · AC-17 — 로그인한 사용자만. 화면이 버튼을 감추는 것과 무관하게 서버가 막는다. */
    @Transactional
    public InquiryDetailResponse create(InquiryCreateRequest request,
            AuthenticatedUser principal) {
        guard.requireCanWrite(BOARD, principal);
        String title = InquiryTexts.requireTitle(request.title());
        String content = InquiryTexts.requireContent(request.content());
        // 비밀글 체크가 꺼져 있으면 비밀번호는 무시한다 — 쓰이지 않는 잠금 값을 남기지 않는다
        String hash = Boolean.TRUE.equals(request.secret())
                ? hashRequired(request.secretPassword())
                : null;

        User author = currentUser(principal);
        Post saved = posts.save(Post.inquiry(author, title, content, hash, OffsetDateTime.now()));
        return unlockedDetail(saved, principal, saved.getViewCount());
    }

    /**
     * AC-19 · AC-20 · AC-26 — 본인 + 미답변일 때만.
     *
     * <p>답변 여부를 <b>저장 시점에 다시 본다</b>. 사용자가 수정 화면을 열어둔 사이 관리자가
     * 답변을 등록할 수 있고, <b>화면을 열 때 미답변이었다는 사실은 권한의 근거가 되지 않는다</b>.
     */
    @Transactional
    public InquiryDetailResponse update(Long id, InquiryUpdateRequest request,
            AuthenticatedUser principal) {
        Post post = require(id);
        // ★ 관리자에게도 열지 않는다 — 관리자가 질문 문구를 바꿀 수 있으면 문답 기록의 신뢰가
        //   사라진다(판단 16). 부적절한 글은 삭제로 처리한다
        guard.requireOwner(principal, post.getAuthor().getId());
        requireUnanswered(id);

        String title = InquiryTexts.requireTitle(request.title());
        String content = InquiryTexts.requireContent(request.content());
        OffsetDateTime now = OffsetDateTime.now();
        post.updateContent(title, content, now);

        boolean secret = Boolean.TRUE.equals(request.secret());
        boolean hasNewPassword = request.secretPassword() != null
                && !request.secretPassword().isEmpty();
        if (!secret) {
            // AC-26 — 해제하면 저장된 비밀번호도 남기지 않는다.
            // 남겨두면 나중에 되돌릴 때 작성자가 잊은 옛 비밀번호가 되살아난다
            post.changeSecret(null, now);
            grants.revokePost(id);
        } else if (hasNewPassword) {
            post.changeSecret(hashRequired(request.secretPassword()), now);
            // 비밀번호를 바꾼 이유는 대개 "그 사람에게서 거두고 싶다" 이다 → 통과도 함께 회수한다
            grants.revokePost(id);
        } else if (!post.isSecret()) {
            // 공개글 → 비밀글로 바꾸는데 값이 없다. 유지할 기존 값도 없다(AC-23)
            throw new ApiException(ErrorCode.SECRET_PASSWORD_REQUIRED);
        }
        // else: 이미 비밀글 + 새 값 없음 → 기존 비밀번호 유지(006 Edge Cases)

        return unlockedDetail(post, principal, post.getViewCount());
    }

    /** AC-21 — 본인 + 미답변일 때만. 답변은 운영 기록이라 질문자가 지우면 무엇에 답했는지 사라진다. */
    @Transactional
    public void delete(Long id, AuthenticatedUser principal) {
        Post post = require(id);
        guard.requireOwner(principal, post.getAuthor().getId());
        requireUnanswered(id);
        posts.delete(post);
        grants.revokePost(id);
    }

    /**
     * 요구사항 6.5 관리자 삭제. <b>답변 여부와 무관</b>하게 허용한다 —
     * 운영상 부적절한 글은 내려야 하고, {@code FR-015} 의 미답변 제한은 <b>작성자에게만</b>
     * 적용된다. 답변 행은 V9 의 {@code ON DELETE CASCADE} 로 함께 지워진다.
     */
    @Transactional
    public void deleteByAdmin(Long id, AuthenticatedUser principal) {
        guard.requireAdmin(principal);
        posts.delete(require(id));
        grants.revokePost(id);
    }

    // ── 내부 ─────────────────────────────────────────────────

    private Post require(Long id) {
        return posts.findByIdAndBoardType(id, BOARD).orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }

    private void requireUnanswered(Long postId) {
        if (answers.existsByPostId(postId)) {
            throw new ApiException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }
    }

    /**
     * 열람 판정 — FR-019. 셋 중 하나면 연다.
     * 순서를 이렇게 둔 이유: 공개글이면 뒤의 두 판정 자체가 필요 없고,
     * 작성자·관리자 확인은 캐시 조회보다 싸다.
     */
    private boolean canRead(Post post, String grantToken, AuthenticatedUser principal) {
        if (!post.isSecret()) {
            return true;
        }
        if (guard.canSkipSecretPassword(principal, post.getAuthor().getId())) {
            return true;
        }
        return grants.isGranted(post.getId(), grantToken, Instant.now());
    }

    private InquiryDetailResponse unlockedDetail(Post post, AuthenticatedUser principal,
            long viewCount) {
        InquiryAnswerResponse answer = answerViews.findByPostId(post.getId())
                .map(InquiryAnswerResponse::of)
                .orElse(null);
        boolean answered = answer != null;
        boolean mine = guard.isOwner(principal, post.getAuthor().getId());
        // 화면 편의값이다. 실제 판정은 update/delete 가 서버에서 다시 한다
        boolean changeable = mine && !answered;
        return new InquiryDetailResponse(post.getId(), post.getTitle(), post.getContent(),
                post.isSecret(), false, answered, viewCount, post.getCreatedAt(),
                post.getUpdatedAt(), post.getAuthor().getName(), mine, changeable, changeable,
                answer);
    }

    /**
     * AC-23 · AC-24 — 비밀글에는 비밀번호가 반드시 있어야 하고, 숫자 4자리여야 한다.
     * 형식 판정은 {@link SecretPostPassword} 하나가 한다(정규식이 두 곳에 있으면 갈라진다).
     * 여기서는 그 예외를 006 의 코드로 바꿔 주기만 한다.
     */
    private String hashRequired(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new ApiException(ErrorCode.SECRET_PASSWORD_REQUIRED);
        }
        try {
            return secretPassword.hash(raw);
        } catch (ApiException ex) {
            throw new ApiException(ErrorCode.SECRET_PASSWORD_FORMAT_INVALID);
        }
    }

    private User currentUser(AuthenticatedUser principal) {
        return users.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED));
    }
}
