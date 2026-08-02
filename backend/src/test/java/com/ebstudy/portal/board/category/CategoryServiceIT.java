package com.ebstudy.portal.board.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.BoardAccessGuard;
import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.Category;
import com.ebstudy.portal.board.common.Post;
import com.ebstudy.portal.board.common.PostRepository;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.support.PostgresContainer;
import com.ebstudy.portal.user.Role;
import com.ebstudy.portal.user.User;
import com.ebstudy.portal.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분류 관리 — 요구사항 7.2.
 *
 * <p><b>웹 계층을 띄우지 않는 것({@code webEnvironment = NONE})은 의도다.</b>
 * 지금 게시판 4종 백엔드가 병렬로 컨트롤러를 만들고 있어서, 웹 컨텍스트를 띄우면
 * <b>남의 미완성 매핑 때문에</b> 이 테스트가 빨개진다. 여기서 확인하려는 것은
 * 리포지토리 파생 쿼리와 서비스 판정이지 HTTP 계약이 아니다.
 * ({@code @DataJpaTest} 슬라이스가 더 좁지만 Boot 4.1 에서 별도 모듈로 빠졌고
 * {@code build.gradle.kts} 는 이번 병렬 작업에서 수정 금지다.)
 *
 * <p>실제 PostgreSQL 에 붙는다({@code test-strategy.md} 5.1) — V5·V6·V10 마이그레이션과
 * CHECK 제약이 실제로 도는지 확인해야 하기 때문이다.
 *
 * <p>{@code @Transactional} 로 매 테스트를 롤백한다. HTTP 를 쓰지 않으므로
 * {@code IntegrationTestBase} 의 TRUNCATE 방식이 필요 없다 —
 * 그쪽은 실제 커밋 경합을 보는 테스트가 있어서 그렇게 한 것이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class CategoryServiceIT {

    private static final AuthenticatedUser ADMIN =
            new AuthenticatedUser(1L, "bossadmin", Role.ADMIN);
    private static final AuthenticatedUser MEMBER =
            new AuthenticatedUser(2L, "member01", Role.USER);

    @Autowired
    private CategoryQueryService categoryQuery;

    @Autowired
    private CategoryAdminService categoryAdmin;

    @Autowired
    private PostRepository posts;

    @Autowired
    private UserRepository users;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PostgresContainer.instance().getJdbcUrl());
        registry.add("spring.datasource.username", () -> PostgresContainer.instance().getUsername());
        registry.add("spring.datasource.password", () -> PostgresContainer.instance().getPassword());
    }

    @Test
    @DisplayName("조회 — 활성 분류만 표시 순서대로. V10 기본값이 실제로 들어와 있다")
    void activeCategoriesAreOrdered() {
        List<CategoryResponse> free = categoryQuery.activeCategoryResponses(BoardType.FREE);

        assertThat(free).extracting(CategoryResponse::name).containsExactly("자유", "질문", "정보");
        assertThat(free).allSatisfy(c -> assertThat(c.boardType()).isEqualTo("FREE"));
    }

    @Test
    @DisplayName("조회 — 문의게시판은 분류가 없다(요구사항 0장 표)")
    void inquiryHasNoCategory() {
        assertThatThrownBy(() -> categoryQuery.activeCategoryResponses(BoardType.INQUIRY))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("등록 — 순서를 안 적으면 맨 뒤에 붙는다")
    void createAppendsToTail() {
        CategoryAdminResponse created =
                categoryAdmin.create(BoardType.FREE, "  잡담  ", null, ADMIN);

        // 앞뒤 공백은 지운다 — 안 지우면 "자유"와 "자유 "가 다른 분류가 되어 유니크를 우회한다
        assertThat(created.name()).isEqualTo("잡담");
        assertThat(created.sortOrder()).isEqualTo(4);
        assertThat(created.active()).isTrue();
        assertThat(created.postCount()).isZero();
        assertThat(created.deletable()).isTrue();
    }

    @Test
    @DisplayName("등록 — 같은 게시판의 같은 이름은 대소문자를 바꿔도 거부한다")
    void createRejectsDuplicateNameIgnoringCase() {
        categoryAdmin.create(BoardType.FREE, "Notice", null, ADMIN);

        assertThatThrownBy(() -> categoryAdmin.create(BoardType.FREE, "notice", null, ADMIN))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("등록 — 다른 게시판이면 같은 이름을 쓸 수 있다")
    void sameNameAllowedOnDifferentBoard() {
        categoryAdmin.create(BoardType.FREE, "행사", null, ADMIN);   // GALLERY 에 이미 있는 이름

        assertThat(categoryQuery.activeCategoryResponses(BoardType.FREE))
                .extracting(CategoryResponse::name).contains("행사");
    }

    @Test
    @DisplayName("등록 — 문의게시판에는 분류를 만들 수 없다(V5 ck_categories_board_type 의 짝)")
    void cannotCreateForInquiry() {
        assertThatThrownBy(() -> categoryAdmin.create(BoardType.INQUIRY, "문의분류", null, ADMIN))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("등록 — 이름이 비었거나 50자를 넘으면 거부한다")
    void createValidatesName() {
        assertThatThrownBy(() -> categoryAdmin.create(BoardType.FREE, "   ", null, ADMIN))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> categoryAdmin.create(BoardType.FREE, "가".repeat(51), null, ADMIN))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("수정 — 자기 이름을 그대로 저장하는 것은 중복이 아니다")
    void renameToOwnNameIsNotDuplicate() {
        CategoryAdminResponse created = categoryAdmin.create(BoardType.FREE, "잡담", null, ADMIN);

        CategoryAdminResponse renamed =
                categoryAdmin.update(created.id(), "잡담", null, null, ADMIN);

        assertThat(renamed.name()).isEqualTo("잡담");
    }

    @Test
    @DisplayName("수정 — 사용 안 함으로 내리면 사용자 목록에서 빠지고 관리 목록에는 남는다")
    void deactivateHidesFromUsersOnly() {
        CategoryAdminResponse created = categoryAdmin.create(BoardType.FREE, "잡담", null, ADMIN);

        categoryAdmin.update(created.id(), null, null, false, ADMIN);

        assertThat(categoryQuery.activeCategoryResponses(BoardType.FREE))
                .extracting(CategoryResponse::name).doesNotContain("잡담");
        assertThat(categoryAdmin.list(BoardType.FREE, ADMIN))
                .extracting(CategoryAdminResponse::name).contains("잡담");
    }

    @Test
    @DisplayName("글 등록 — 비활성 분류는 새 글에 붙일 수 없다. 화면에서 감춘 것은 검증이 아니다")
    void inactiveCategoryIsNotSelectable() {
        CategoryAdminResponse created = categoryAdmin.create(BoardType.FREE, "잡담", null, ADMIN);
        categoryAdmin.update(created.id(), null, null, false, ADMIN);

        assertThatThrownBy(() -> categoryQuery.resolveForPost(BoardType.FREE, created.id()))
                .isInstanceOf(ApiException.class);
        // 이미 저장된 글을 읽는 경로는 비활성이어도 열려야 한다
        assertThat(categoryQuery.requireAny(BoardType.FREE, created.id()).getName())
                .isEqualTo("잡담");
    }

    @Test
    @DisplayName("글 등록 — 다른 게시판의 분류 id 로 우회할 수 없다")
    void cannotBorrowAnotherBoardsCategory() {
        Long galleryCategoryId =
                categoryQuery.activeCategories(BoardType.GALLERY).getFirst().getId();

        assertThatThrownBy(() -> categoryQuery.resolveForPost(BoardType.FREE, galleryCategoryId))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("글 등록 — 문의는 분류가 null 이어야 하고, 나머지는 null 이면 거부한다")
    void resolveForPostFollowsBoardRules() {
        assertThat(categoryQuery.resolveForPost(BoardType.INQUIRY, null)).isNull();

        Long freeCategoryId = categoryQuery.activeCategories(BoardType.FREE).getFirst().getId();
        assertThatThrownBy(() -> categoryQuery.resolveForPost(BoardType.INQUIRY, freeCategoryId))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> categoryQuery.resolveForPost(BoardType.FREE, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("삭제 — 한 번도 쓰이지 않은 분류는 지운다")
    void deleteUnusedCategory() {
        CategoryAdminResponse created = categoryAdmin.create(BoardType.FREE, "오타분뉴", null, ADMIN);

        categoryAdmin.delete(created.id(), ADMIN);

        assertThat(categoryAdmin.list(BoardType.FREE, ADMIN))
                .extracting(CategoryAdminResponse::name).doesNotContain("오타분뉴");
    }

    @Test
    @DisplayName("삭제 — ★ 요구사항 7.2. 글이 쓰고 있는 분류는 삭제하지 않는다")
    void cannotDeleteCategoryInUse() {
        Category category = categoryQuery.activeCategories(BoardType.FREE).getFirst();
        posts.saveAndFlush(Post.free(category, someUser(), "제목", "내용", OffsetDateTime.now()));

        assertThatThrownBy(() -> categoryAdmin.delete(category.getId(), ADMIN))
                .isInstanceOf(ApiException.class);

        // 관리 목록이 그 사실을 미리 알려 준다 — 화면이 "삭제" 대신 "사용 안 함"을 권할 수 있게
        assertThat(categoryAdmin.list(BoardType.FREE, ADMIN))
                .filteredOn(c -> c.id().equals(category.getId()))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.postCount()).isEqualTo(1L);
                    assertThat(c.deletable()).isFalse();
                });
    }

    @Test
    @DisplayName("권한 — 관리 API 는 관리자만. SecurityConfig 밖에서도 서버가 다시 막는다")
    void adminOnly() {
        assertThatThrownBy(() -> categoryAdmin.list(BoardType.FREE, MEMBER))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> categoryAdmin.create(BoardType.FREE, "잡담", null, MEMBER))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> categoryAdmin.create(BoardType.FREE, "잡담", null, null))
                .isInstanceOf(ApiException.class);
    }

    private User someUser() {
        return users.saveAndFlush(User.create("writer01", "{noop}x", "작성자", Role.USER,
                OffsetDateTime.now()));
    }
}
