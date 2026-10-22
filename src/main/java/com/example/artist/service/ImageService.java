
package com.example.artist.service;

import com.example.artist.dto.ClipSearchResponse;
import com.example.artist.model.ImageEntity;
import com.example.artist.repository.ImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 图片检索服务：通过 CLIP 微服务做文搜图和图搜图。
 *
 * 优化要点：
 * 1. 抽取统一的错误拦截器，消除三处 WebClient 重复代码（保持 retrieve 链式结构）。
 * 2. 所有下游调用增加超时保护，避免 CLIP 服务挂起导致 block 永久阻塞。
 * 3. toEntities 批量查询的 toMap 增加合并函数，防御重复 faissId 抛异常。
 * 4. indexLocalLibrary 用安全数值解析替代 unchecked 强转。
 * 5. searchByImage 增加参数重载，与 searchByText 调用形态保持一致。
 * 6. 检索/索引路径统一补充耗时日志，便于线上定位慢调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private final WebClient clipWebClient;
    private final ImageRepository imageRepository;

    @Value("${image.search.top-k:8}")
    private int topK;

    @Value("${image.search.threshold:0.2}")
    private double threshold;

    /** 下游 CLIP 检索调用超时（秒），防止服务挂起时 block 永久等待。 */
    @Value("${image.search.timeout-seconds:30}")
    private long timeoutSeconds;

    /** 索引重建耗时较长，单独给更宽松的超时（秒）。 */
    @Value("${image.search.index-timeout-seconds:300}")
    private long indexTimeoutSeconds;

    // ---------------------------------------------------------------------
    // 对外接口
    // ---------------------------------------------------------------------

    /**
     * 文搜图默认重载：使用 @Value 注入的 topK 和 threshold 默认值。
     * Controller 在不传参时调用此方法。
     */
    public List<ImageEntity> searchByText(String query) {
        return searchByText(query, this.topK, this.threshold);
    }

    /**
     * 文搜图：文字描述 -> CLIP 编码 -> FAISS 检索 -> 返回匹配图片。
     */
    public List<ImageEntity> searchByText(String query, int topK, double threshold) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("搜索关键词不能为空");
        }

        long start = System.currentTimeMillis();
        log.info("Text search: {} (topK={}, threshold={})", query, topK, threshold);

        ClipSearchResponse resp = clipWebClient.post()
                .uri("/search/text")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "query", query,
                        "topK", topK,
                        "threshold", threshold))
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorInterceptor("文搜图"))
                .bodyToMono(ClipSearchResponse.class)
                .block(Duration.ofSeconds(timeoutSeconds));

        log.info("Text search 完成: 结果数={}, 耗时={}ms",
                sizeOf(resp), System.currentTimeMillis() - start);
        return toEntities(resp);
    }

    /**
     * 图搜图默认重载：使用注入的 topK 和 threshold 默认值。
     */
    public List<ImageEntity> searchByImage(MultipartFile file) {
        return searchByImage(file, this.topK, this.threshold);
    }

    /**
     * 图搜图：上传图片 -> CLIP 编码 -> FAISS 检索。
     */
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

    /**
     * 扫描本地图库目录，对所有图片做 CLIP 编码并建 FAISS 索引。
     */
    @SuppressWarnings("unchecked")
    public int indexLocalLibrary() {
        long start = System.currentTimeMillis();
        log.info("触发 CLIP 索引重建");

        Map<String, Object> resp;
        try {
            resp = clipWebClient.post()
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

    /**
     * 统一的错误拦截器工厂：非 2xx 时读取响应体打印日志，并返回带状态码的异常 Mono。
     * 适配 Spring Boot 3.3.5，onStatus 使用单参数 response 形态，三处调用复用同一段逻辑。
     *
     * @param scene 业务场景名，用于区分日志与异常信息
     */
    private Function<ClientResponse, Mono<? extends Throwable>> errorInterceptor(String scene) {
        return response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .doOnNext(body -> log.error("{} 调用失败: status={}, body={}",
                        scene, response.statusCode(), body))
                .then(Mono.error(new IllegalStateException(
                        scene + "服务调用失败: " + response.statusCode())));
    }

    /**
     * 安全地从 /index 返回体解析 indexedCount，兼容 Number / 字符串，避免 unchecked 强转异常。
     */
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

    /** 返回响应结果条数，null 安全。 */
    private int sizeOf(ClipSearchResponse resp) {
        return resp == null || resp.items() == null ? 0 : resp.items().size();
    }

    /**
     * 把 CLIP 微服务返回的 {id, path, score} 列表映射成业务实体。
     * 批量查询消除 N+1；toMap 增加合并函数防御重复 faissId 抛 IllegalStateException。
     */
    private List<ImageEntity> toEntities(ClipSearchResponse resp) {
        if (resp == null || resp.items() == null || resp.items().isEmpty()) {
            return List.of();
        }

        // 1. 收集所有 id 进行批量查询
        List<String> faissIds = resp.items().stream()
                .map(ClipSearchResponse.ClipItem::id)
                .toList();

        // 2. 批量查询数据库并放入 Map（重复 faissId 保留先出现的一条）
        Map<String, ImageEntity> entityMap = imageRepository.findAllByFaissIdIn(faissIds)
                .stream()
                .collect(Collectors.toMap(
                        ImageEntity::getFaissId,
                        Function.identity(),
                        (existing, replacement) -> existing));

        // 3. 组装返回结果：数据库有记录则补全元数据，没有则用临时实体兜底
        List<ImageEntity> result = new ArrayList<>(resp.items().size());
        for (ClipSearchResponse.ClipItem item : resp.items()) {
            ImageEntity entity = entityMap.getOrDefault(item.id(), new ImageEntity());
            entity.setFaissId(item.id());
            entity.setFilePath(item.path());
            entity.setScore(item.score());
            result.add(entity);
        }
        return result;
    }
}