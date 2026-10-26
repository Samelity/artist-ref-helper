package com.example.artist.repository;

import com.example.artist.model.BoardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<BoardEntity, Long> {

    @Query("SELECT b FROM BoardEntity b LEFT JOIN FETCH b.images WHERE b.id = :id")
    Optional<BoardEntity> findByIdWithImages(@Param("id") Long id);

    @Query("SELECT b FROM BoardEntity b LEFT JOIN FETCH b.images ORDER BY b.createdAt DESC")
    List<BoardEntity> findAllWithImages();
}