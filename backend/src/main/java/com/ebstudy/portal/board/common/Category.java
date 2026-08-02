package com.ebstudy.portal.board.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 분류 — V5 {@code categories}. 요구사항 7.2.
 *
 * <p>삭제 메서드가 없는 것은 의도다 — 요구사항 7.2 가 <i>"이미 사용 중인 분류는 삭제하지
 * 않는다. 비활성으로 내린다"</i> 로 정했고, V6 의 {@code fk_posts_categories ON DELETE RESTRICT}
 * 가 DB 쪽 짝이다. 지우는 대신 {@code deactivate(now)} 를 쓴다.
 */
@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 게시판이 바뀌면 그 분류를 쓰던 글의 분류가 통째로 어긋난다 → 만든 뒤 바꾸지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "board_type", nullable = false, length = 20, updatable = false)
    private BoardType boardType;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private Category(BoardType boardType, String name, int sortOrder, OffsetDateTime now) {
        this.boardType = boardType;
        this.name = name;
        this.sortOrder = sortOrder;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Category create(BoardType boardType, String name, int sortOrder,
            OffsetDateTime now) {
        return new Category(boardType, name, sortOrder, now);
    }

    public void rename(String name, OffsetDateTime now) {
        this.name = name;
        this.updatedAt = now;
    }

    public void reorder(int sortOrder, OffsetDateTime now) {
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    /** 요구사항 7.2 — 삭제 대신 이것을 쓴다. 과거 글의 분류명이 사라지지 않는다. */
    public void deactivate(OffsetDateTime now) {
        this.active = false;
        this.updatedAt = now;
    }

    public void activate(OffsetDateTime now) {
        this.active = true;
        this.updatedAt = now;
    }
}
