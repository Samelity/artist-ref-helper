package com.example.artist.dto;

public record BoardBatchAddResponse(
        BoardSummaryResponse board,
        int added,
        int skipped
) {
}
