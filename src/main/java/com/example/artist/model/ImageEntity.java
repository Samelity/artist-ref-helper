package com.example.artist.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "reference_image")
@NoArgsConstructor
public class ImageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "faiss_id", unique = true)
    private Long faissId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** CLIP 相关度：非数据库列，仅本次检索响应使用。 */
    @Transient
    private Double score;

    // getter / setter 全部生成（getFaissId 返回 Long，供 toEntities 的 Map key 使用）
}