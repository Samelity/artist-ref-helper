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

    /** 图片来源：local / pexels / url */
    @Column(name = "source")
    private String source;

    /** 来源平台的图片 ID（如 Pexels photo id） */
    @Column(name = "source_id")
    private String sourceId;

    /** 原始下载 URL */
    @Column(name = "source_url")
    private String sourceUrl;

    /** 摄影师 / 作者 */
    @Column(name = "photographer")
    private String photographer;

    /** CLIP 相关度：非数据库列，仅本次检索响应使用。 */
    @Transient
    private Double score;
}