package com.example.artist.dto;

import com.example.artist.model.ImageEntity;

import java.util.List;

/**
 * 图库浏览分页响应。
 */
public record ImagePageResponse(
        List<ImageEntity> items,
        int page,
        int size,
        long total,
        int totalPages) {
}
