package com.example.artist.dto;

public record BoardBatchAddResponse(
        BoardResponse board,
        int added,
        int skipped
) {
}
