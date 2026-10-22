package com.example.artist.controller;

import com.example.artist.model.ImageEntity;
import com.example.artist.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 图片检索接口：文搜图、图搜图。
 */
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    /**
     * 文搜图：输入文字描述，返回相似图片。
     */
    @GetMapping("/search/text")
    public List<ImageEntity> searchByText(@RequestParam String query,
                                          @RequestParam(required = false) Integer topK,
                                          @RequestParam(required = false) Double threshold) {
        if (topK != null && threshold != null) {
            return imageService.searchByText(query, topK, threshold);
        }
        return imageService.searchByText(query);
    }

    /**
     * 图搜图：上传图片，返回相似图片。
     */
    @PostMapping("/search/similar")
    public ResponseEntity<?> searchByImage(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(imageService.searchByImage(file));
    }

    /**
     * 扫描并索引本地图库。
     */
    @PostMapping("/index")
    public ResponseEntity<Map<String, Object>> indexLibrary() {
        int count = imageService.indexLocalLibrary();
        return ResponseEntity.ok(Map.of("indexedCount", count));
    }
}