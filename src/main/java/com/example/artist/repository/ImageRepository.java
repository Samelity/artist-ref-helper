package com.example.artist.repository;

import com.example.artist.model.ImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ImageRepository extends JpaRepository<ImageEntity, Long> {

    /** FAISS 行号（Long）批量反查图片元数据。 */
    List<ImageEntity> findAllByFaissIdInAndFaissIdIsNotNull(List<Long> faissIds);
}