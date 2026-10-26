package com.example.artist.service;

import com.example.artist.dto.ClipSearchResponse;
import com.example.artist.model.ImageEntity;
import com.example.artist.repository.ImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 图片检索服务：通过 CLIP 微服务做文搜图和图搜图，并用 PostgreSQL 反查补全元数据。
 * <p>
 * day5 关键改动：
 * 1. CLIP 只负责向量检索，返回 {id(FAISS行号), fileName, filePath, score}。
 * 2. 业务层用 FAISS 行号批量反查 reference_image 表（faiss_id BIGINT），
 * 以库中 file_name / file_path 为权威元数据；库里没有才用 CLIP 返回值兜底。
 * 3. FAISS 行号在 Python 侧是字符串，库里是 BIGINT，统一在此处做 String -> Long 转换。
 */
@Slf4j
@Service
public class ImageService {

    private final WebClient clipWebClient;
    private final WebClient indexWebClient;
    private final ImageRepository imageRepository;

    public ImageService(
            @Qualifier("clipWebClient") WebClient clipWebClient,
            @Qualifier("indexWebClient") WebClient indexWebClient,
            ImageRepository imageRepository) {
        this.clipWebClient = clipWebClient;
        this.indexWebClient = indexWebClient;
        this.imageRepository = imageRepository;
    }

    @Value("${image.search.top-k:8}")
    private int topK;

    @Value("${image.search.threshold:0.2}")
    private double threshold;

    /**
     * 下游 CLIP 检索调用超时（秒），防止服务挂起时 block 永久等待。
     */
    @Value("${image.search.timeout-seconds:30}")
    private long timeoutSeconds;

    /**
     * 索引重建耗时较长，单独给更宽松的超时（秒）。
     */
    @Value("${image.search.index-timeout-seconds:300}")
    private long indexTimeoutSeconds;

    // ---------------------------------------------------------------------
    // 对外接口（searchByText / searchByImage / indexLocalLibrary 同 _4，略）
    // ---------------------------------------------------------------------

    public List<ImageEntity> searchByText(String query) {
        return searchByText(query, this.topK, this.threshold);
    }

    public List<ImageEntity> searchByText(String query, int topK, double threshold) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("搜索关键词不能为空");
        }

        long start = System.currentTimeMillis();
        log.info("Text search: {} (topK={}, threshold={})", query, topK, threshold);

        ClipSearchResponse resp = clipWebClient.post()
                .uri("/search/text")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query, "topK", topK, "threshold", threshold))
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorInterceptor("文搜图"))
                .bodyToMono(ClipSearchResponse.class)
                .block(Duration.ofSeconds(timeoutSeconds));

        log.info("Text search 完成: 结果数={}, 耗时={}ms",
                sizeOf(resp), System.currentTimeMillis() - start);
        return toEntities(resp);
    }

    public List<ImageEntity> searchByImage(MultipartFile file) {
        return searchByImage(file, this.topK, this.threshold);
    }

    public List<ImageEntity> searchByImage(MultipartFile file, int topK, double threshold) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("搜索图片不能为空");
        }

        long start = System.currentTimeMillis();
        log.info("Image search with file: {} (topK={}, threshold={})",
                file.getOriginalFilename(), topK, threshold);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败: " + file.getOriginalFilename(), e);
        }

        String filename = file.getOriginalFilename() == null ? "upload.jpg" : file.getOriginalFilename();
        MediaType partType = file.getContentType() == null
                ? MediaType.IMAGE_JPEG
                : MediaType.parseMediaType(file.getContentType());

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(partType);
        builder.part("topK", String.valueOf(topK));
        builder.part("threshold", String.valueOf(threshold));

        ClipSearchResponse resp = clipWebClient.post()
                .uri("/search/image")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorInterceptor("图搜图"))
                .bodyToMono(ClipSearchResponse.class)
                .block(Duration.ofSeconds(timeoutSeconds));

        log.info("Image search 完成: 结果数={}, 耗时={}ms",
                sizeOf(resp), System.currentTimeMillis() - start);
        return toEntities(resp);
    }

    @SuppressWarnings("unchecked")
    public int indexLocalLibrary() {
        long start = System.currentTimeMillis();
        log.info("触发 CLIP 索引重建");

        Map<String, Object> resp;
        try {
            resp = indexWebClient.post()
                    .uri("/index")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, errorInterceptor("CLIP /index"))
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(indexTimeoutSeconds));
        } catch (Exception e) {
            log.error("索引重建失败", e);
            throw e;
        }

        int count = extractIndexedCount(resp);
        log.info("索引重建完成: indexedCount={}, 耗时={}ms",
                count, System.currentTimeMillis() - start);
        return count;
    }

    // ---------------------------------------------------------------------
    // 私有辅助方法
    // ---------------------------------------------------------------------

    private Function<ClientResponse, Mono<? extends Throwable>> errorInterceptor(String scene) {
        return response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .doOnNext(body -> log.error("{} 调用失败: status={}, body={}",
                        scene, response.statusCode(), body))
                .then(Mono.error(new IllegalStateException(
                        scene + "服务调用失败: " + response.statusCode())));
    }

    private int extractIndexedCount(Map<String, Object> resp) {
        if (resp == null) {
            return 0;
        }
        Object raw = resp.get("indexedCount");
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String str && !str.isBlank()) {
            try {
                return (int) Double.parseDouble(str.trim());
            } catch (NumberFormatException ignore) {
                return 0;
            }
        }
        return 0;
    }

    private int sizeOf(ClipSearchResponse resp) {
        return resp == null || resp.items() == null ? 0 : resp.items().size();
    }

    /**
     * 把 CLIP 返回的 {id(FAISS行号), fileName, filePath, score} 映射为业务实体。
     * 关键：用 FAISS 行号（String -> Long）批量反查 reference_image，
     * 库中元数据为准，查不到才用 CLIP 返回值兜底；保持 FAISS 返回的原始顺序。
     */
    private List<ImageEntity> toEntities(ClipSearchResponse resp) {
        if (resp == null || resp.items() == null || resp.items().isEmpty()) {
            return List.of();
        }

        // 1. 把 FAISS 行号 String -> Long，跳过无法解析的脏数据
        List<Long> faissIds = resp.items().stream()
                .map(item -> parseFaissId(item.id()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 2. 批量反查 PostgreSQL（faiss_id 有唯一索引），放入 Map，重复保留先出现的
        Map<Long, ImageEntity> entityMap = faissIds.isEmpty()
                ? Map.of()
                : imageRepository.findAllByFaissIdInAndFaissIdIsNotNull(faissIds).stream()
                .collect(Collectors.toMap(
                        ImageEntity::getFaissId,
                        Function.identity(),
                        (existing, replacement) -> existing));

        // 3. 按 FAISS 原始顺序组装：库里有记录则补全 fileName/filePath，否则用 CLIP 兜底
        List<ImageEntity> result = new ArrayList<>(resp.items().size());
        for (ClipSearchResponse.ClipItem item : resp.items()) {
            Long faissId = parseFaissId(item.id());
            ImageEntity db = faissId == null ? null : entityMap.get(faissId);

            ImageEntity entity = db != null ? db : new ImageEntity();
            entity.setFaissId(faissId);          // 回填行号，便于前端再次定位
            // 库为权威元数据；库里缺字段（或库里没这条记录）时用 CLIP 返回值兜底
            if (entity.getFileName() == null) {
                entity.setFileName(item.fileName());
            }
            if (entity.getFilePath() == null) {
                entity.setFilePath(item.filePath());
            }
            entity.setScore(item.score());       // score 是非持久化字段，仅用于本次响应
            result.add(entity);
        }
        return result;
    }

    /**
     * FAISS 行号 String -> Long，无法解析返回 null（脏数据不拖垮整次检索）。
     */
    private Long parseFaissId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("无法解析的 FAISS id: {}", raw);
            return null;
        }
    }
}