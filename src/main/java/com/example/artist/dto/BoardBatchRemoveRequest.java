package com.example.artist.dto;

import java.util.List;

public record BoardBatchRemoveRequest(List<Long> imageIds) {
}
