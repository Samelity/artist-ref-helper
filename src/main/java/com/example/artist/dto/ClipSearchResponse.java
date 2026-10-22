package com.example.artist.dto;

import java.util.List;

public record ClipSearchResponse(List<ClipItem> items) {
    public record ClipItem(String id, String fileName, String filePath, double score) {}
}