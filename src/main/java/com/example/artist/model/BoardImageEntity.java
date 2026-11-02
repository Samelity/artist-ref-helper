package com.example.artist.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;

/**
 * 灵感板与图片的关联行：一行代表一次收藏，addedAt 即加入时间。
 * 表结构沿用原 @ManyToMany 生成的 board_images（主键仍为 board_id + image_id）。
 */
@Entity
@Table(name = "board_images")
@IdClass(BoardImageId.class)
@Getter
@NoArgsConstructor
public class BoardImageEntity implements Persistable<BoardImageId> {

    @Id
    @Column(name = "board_id", updatable = false)
    private Long boardId;

    @Id
    @Column(name = "image_id", updatable = false)
    private Long imageId;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private OffsetDateTime addedAt;

    /** 只读：外键值由 imageId 写入，这里仅供查询取图片信息。 */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "image_id", insertable = false, updatable = false)
    private ImageEntity image;

    public BoardImageEntity(Long boardId, Long imageId) {
        this.boardId = boardId;
        this.imageId = imageId;
    }

    @Override
    public BoardImageId getId() {
        return new BoardImageId(boardId, imageId);
    }

    /** 复合主键永不为空，用 added_at 是否为空区分新收藏与已入库的关联行，避免 save 走成先查后写。 */
    @Override
    public boolean isNew() {
        return addedAt == null;
    }
}
