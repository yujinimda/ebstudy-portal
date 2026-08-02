package com.ebstudy.portal.board.free;

import com.ebstudy.portal.auth.AuthenticatedUser;
import com.ebstudy.portal.board.common.BoardAccessGuard;
import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.board.common.Comment;
import com.ebstudy.portal.board.common.Post;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import com.ebstudy.portal.user.User;
import com.ebstudy.portal.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자유게시판 댓글 — 요구사항 4.2.
 *
 * <p>권한 규칙이 글과 다르다는 것이 이 클래스를 따로 둔 이유다:
 * <ul>
 *   <li>작성은 <b>로그인 사용자</b>(글과 같다)</li>
 *   <li>목록은 <b>누구나</b>(요구사항 1.3)</li>
 *   <li>삭제는 본인 <b>또는 관리자</b> — 글 삭제는 관리자에게 열려 있지 않다(요구사항 1.3)</li>
 * </ul>
 * 판정 자체는 {@link BoardAccessGuard} 한 곳에서만 한다.
 *
 * <p>수정 기능이 없는 것은 의도다 — 요구사항 4.2 에 없다. 있는 것만 만든다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FreeCommentService {

    private static final BoardType BOARD = BoardType.FREE;
    /** V7 {@code comments.content VARCHAR(1000)} 과 같은 값. 기획에 규정이 없어 정한 값이다. */
    private static final int CONTENT_MAX = 1000;

    private final FreeCommentRepository comments;
    private final FreePostRepository posts;
    private final UserRepository users;
    private final BoardAccessGuard guard;

    /** 상세 화면과 별개로 댓글만 다시 읽는 경로(작성·삭제 후 갱신). */
    @Transactional(readOnly = true)
    public List<FreeCommentItem> list(Long postId, AuthenticatedUser principal) {
        requirePost(postId);
        return itemsOf(postId, principal);
    }

    /** 상세 조회가 이미 글을 확인한 뒤에 부르는 경로 — 글 존재 확인을 두 번 하지 않는다. */
    List<FreeCommentItem> itemsOf(Long postId, AuthenticatedUser principal) {
        return comments.findWithAuthorByPostIdOrderByCreatedAtAscIdAsc(postId).stream()
                .map(comment -> FreeCommentItem.of(comment, canDelete(principal, comment)))
                .toList();
    }

    /** 요구사항 4.2 — 입력은 로그인한 사용자만. */
    @Transactional
    public Long create(Long postId, AuthenticatedUser principal, FreeCommentWriteRequest request) {
        guard.requireLogin(principal);
        Post post = requirePost(postId);
        String content = requireContent(request == null ? null : request.content());
        User author = users.findById(principal.userId())
                // 토큰은 유효한데 계정이 사라진 상태. 401 로 돌려보내 다시 로그인하게 한다
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED));
        return comments.save(Comment.create(post, author, content, OffsetDateTime.now())).getId();
    }

    /** 요구사항 1.3 — 본인 댓글만. <b>관리자는 모든 댓글</b>. */
    @Transactional
    public void delete(Long postId, Long commentId, AuthenticatedUser principal) {
        Comment comment = comments.findById(commentId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));
        // 다른 글의 댓글 id 를 이 경로로 넣는 우회를 막는다.
        // 프록시에서 id 만 꺼내므로 글을 다시 읽지 않는다
        if (!comment.getPost().getId().equals(postId)) {
            throw new ApiException(ErrorCode.COMMENT_NOT_FOUND);
        }
        guard.requireOwnerOrAdmin(principal, comment.getAuthor().getId());
        comments.delete(comment);
    }

    private boolean canDelete(AuthenticatedUser principal, Comment comment) {
        Long authorId = comment.getAuthor() == null ? null : comment.getAuthor().getId();
        return guard.isOwner(principal, authorId) || guard.isAdmin(principal);
    }

    /** 자유게시판 글인지까지 확인한다 — 다른 게시판 글에 댓글을 다는 경로를 만들지 않는다. */
    private Post requirePost(Long postId) {
        if (postId == null) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }
        return posts.findWithAuthorAndCategoryByIdAndBoardType(postId, BOARD)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }

    /**
     * 길이는 <b>문자 수</b>로 센다 — 이모지 하나가 2로 세어지면 사용자는 이유를 알 수 없다
     * (001 {@code SignupService.charCount} 와 같은 규칙).
     */
    private String requireContent(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new ApiException(ErrorCode.COMMENT_CONTENT_REQUIRED);
        }
        if (value.codePointCount(0, value.length()) > CONTENT_MAX) {
            throw new ApiException(ErrorCode.COMMENT_CONTENT_LENGTH_INVALID);
        }
        return value;
    }
}
