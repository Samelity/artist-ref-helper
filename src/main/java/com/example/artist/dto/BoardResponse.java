package com.example.artist.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BoardResponse(
        Long id,
        String name,
        String description,
        List<BoardImageResponse> images,
        OffsetDateTime createdAt
) {
    public record BoardImageResponse(
            Long id,
            Long faissId,
            String fileName,
            String filePath,
            String source,
            String sourceId,
            String sourceUrl,
            String photographer
    ) {}
}
