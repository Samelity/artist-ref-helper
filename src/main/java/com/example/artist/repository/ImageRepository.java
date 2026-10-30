package com.example.artist.repository;

import com.example.artist.model.ImageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ImageRepository extends JpaRepository<ImageEntity, Long> {

    /** FAISS 行号（Long）批量反查图片元数据。 */
    List<ImageEntity> findAllByFaissIdInAndFaissIdIsNotNull(List<Long> faissIds);

    /**
     * 图库浏览：FAISS 行号倒序，最新入库的排在最前。
     * 尚未建立索引（faiss_id 为 NULL）的图片排在最后。
     */
    @Query(value = "SELECT * FROM reference_image ORDER BY faiss_id DESC NULLS LAST, id DESC",
            countQuery = "SELECT COUNT(*) FROM reference_image",
            nativeQuery = true)
    Page<ImageEntity> findAllNewestFirst(Pageable pageable);
}