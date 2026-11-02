package com.example.artist.repository;

import com.example.artist.model.BoardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BoardRepository extends JpaRepository<BoardEntity, Long> {

    @Query("SELECT b FROM BoardEntity b ORDER BY b.createdAt DESC")
    List<BoardEntity> findAllOrdered();
}