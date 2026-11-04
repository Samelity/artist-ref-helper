package com.example.artist.dto;

import java.util.List;

/**
 * Python {@code /duplicates/scan} 的响应：只包含候选分组与数据库 id。
 */
public record DuplicateScanResponse(
        double threshold,
        int scanned,
        List<Group> groups,
        boolean cached,
        long elapsedMs) {

    public record Group(List<Member> members, double minScore, double maxScore) {
    }

    public record Member(long imageId, long faissId, double score) {
    }
}
