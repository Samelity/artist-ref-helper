package com.example.artist.dto;

public record BoardBatchRemoveResponse(
        BoardSummaryResponse board,
        int removed
) {
}
