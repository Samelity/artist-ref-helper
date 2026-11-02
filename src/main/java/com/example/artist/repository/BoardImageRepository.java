package com.example.artist.repository;

import com.example.artist.model.BoardImageEntity;
import com.example.artist.model.BoardImageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 收藏关系查询：板内图片一律按加入时间倒序，同一批加入的用 image_id 倒序兜底。
 */
public interface BoardImageRepository extends JpaRepository<BoardImageEntity, BoardImageId> {

    @Query("""
            SELECT bi FROM BoardImageEntity bi JOIN FETCH bi.image
            WHERE bi.boardId = :boardId
            ORDER BY bi.addedAt DESC, bi.imageId DESC
            """)
    List<BoardImageEntity> findOrderedByBoardId(@Param("boardId") Long boardId);

    @Query("""
            SELECT bi FROM BoardImageEntity bi JOIN FETCH bi.image
            WHERE bi.boardId IN :boardIds
            ORDER BY bi.boardId, bi.addedAt DESC, bi.imageId DESC
            """)
    List<BoardImageEntity> findOrderedByBoardIds(@Param("boardIds") Collection<Long> boardIds);

    @Query("SELECT bi.imageId FROM BoardImageEntity bi WHERE bi.boardId = :boardId")
    Set<Long> findImageIdsByBoardId(@Param("boardId") Long boardId);

    @Modifying
    @Query("DELETE FROM BoardImageEntity bi WHERE bi.boardId = :boardId AND bi.imageId = :imageId")
    void deleteByBoardIdAndImageId(@Param("boardId") Long boardId, @Param("imageId") Long imageId);

    @Modifying
    @Query("DELETE FROM BoardImageEntity bi WHERE bi.boardId = :boardId")
    void deleteByBoardId(@Param("boardId") Long boardId);
}
