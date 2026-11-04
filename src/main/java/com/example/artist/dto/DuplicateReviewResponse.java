package com.example.artist.dto;

import com.example.artist.model.ImageEntity;

import java.util.List;

/**
 * 相似图审查页的响应：图片元数据已由数据库补全，并附带建议保留项。
 */
public record DuplicateReviewResponse(
        double threshold,
        int scanned,
        boolean cached,
        long elapsedMs,
        int totalGroups,
        int totalImages,
        List<Group> groups) {

    public record Group(
            List<ImageEntity> images,
            Long suggestedKeepId,
            double minScore,
            double maxScore) {
    }
}
