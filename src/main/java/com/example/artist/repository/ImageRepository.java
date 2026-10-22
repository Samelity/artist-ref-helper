package com.example.artist.repository;

import com.example.artist.model.ImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImageRepository extends JpaRepository<ImageEntity, Long> {
    Optional<ImageEntity> findByFaissId(String faissId);

    /** 批量按 faissId 查询，供 toEntities 消除 N+1。 */
    List<ImageEntity> findAllByFaissIdIn(Collection<String> faissIds);
}