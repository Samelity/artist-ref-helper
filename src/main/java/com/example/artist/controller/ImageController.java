package com.example.artist.controller;

import com.example.artist.model.ImageEntity;
import com.example.artist.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 图片检索接口：文搜图、图搜图。
 */
@Slf4j
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
        log.info("[HTTP] GET /search/text query={} topK={} threshold={}", query, topK, threshold);
        if (topK != null && threshold != null) {
            return imageService.searchByText(query, topK, threshold);
        }
        return imageService.searchByText(query);
    }

    /**
     * 图搜图：上传图片，返回相似图片。
     */
    @PostMapping("/search/similar")
    public ResponseEntity<?> searchByImage(@RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) Integer topK,
                                           @RequestParam(required = false) Double threshold) {
        log.info("[HTTP] POST /search/similar file={} size={} topK={} threshold={}",
                file.getOriginalFilename(), file.getSize(), topK, threshold);
        if (topK != null && threshold != null) {
            return ResponseEntity.ok(imageService.searchByImage(file, topK, threshold));
        }
        return ResponseEntity.ok(imageService.searchByImage(file));
    }

    /**
     * 扫描并索引本地图库。
     */
    @PostMapping("/index")
    public ResponseEntity<Map<String, Object>> indexLibrary() {
        log.info("[HTTP] POST /index 触发索引重建");
        int count = imageService.indexLocalLibrary();
        return ResponseEntity.ok(Map.of("indexedCount", count));
    }
}