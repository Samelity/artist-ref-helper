package com.example.artist.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 图库图片实体。
 */
@Entity
@Table(name = "images")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    private String filePath;

    private Long fileSize;

    private Integer width;

    private Integer height;

    /** 来源类型：LOCAL / UNSPLASH / PEXELS */
    private String sourceType;

    private String sourceUrl;

    /** CLIP 向量 ID（映射 FAISS 索引） */
    private Long vectorId;

    // 关联 CLIP/FAISS 索引里的 ID，入库
    private String faissId;

    // 本次检索相似度，仅返回用，不落库
    @Transient
    private Double score;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}