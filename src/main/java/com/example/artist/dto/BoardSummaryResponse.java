package com.example.artist.dto;

import java.time.OffsetDateTime;

/**
 * 灵感板摘要：列表和写操作只返回元信息与图片数量，不携带图片明细。
 */
public record BoardSummaryResponse(
        Long id,
        String name,
        String description,
        OffsetDateTime createdAt,
        int imageCount
) {
}
