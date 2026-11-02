package com.example.artist.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * board_images 的复合主键，字段名与类型必须和实体里的 @Id 字段一致。
 */
public class BoardImageId implements Serializable {

    private Long boardId;
    private Long imageId;

    public BoardImageId() {
    }

    public BoardImageId(Long boardId, Long imageId) {
        this.boardId = boardId;
        this.imageId = imageId;
    }

    public Long getBoardId() {
        return boardId;
    }

    public void setBoardId(Long boardId) {
        this.boardId = boardId;
    }

    public Long getImageId() {
        return imageId;
    }

    public void setImageId(Long imageId) {
        this.imageId = imageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BoardImageId other)) {
            return false;
        }
        return Objects.equals(boardId, other.boardId) && Objects.equals(imageId, other.imageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(boardId, imageId);
    }
}
