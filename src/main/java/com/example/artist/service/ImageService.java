package com.example.artist.service;

import com.example.artist.dto.ClipSearchResponse;
import com.example.artist.dto.DuplicateReviewResponse;
import com.example.artist.dto.DuplicateScanResponse;
import com.example.artist.dto.ImagePageResponse;
import com.example.artist.model.ImageEntity;
import com.example.artist.repository.ImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 图片检索服务：通过 CLIP 微服务做文搜图和图搜图，并用 PostgreSQL 反查补全元数据。
 * <p>
 * day5 关键改动：
 * 1. CLIP 只负责向量检索，返回 {id(FAISS行号), fileName, filePath, score}。
 * 2. 业务层优先用 imageId（数据库主键）批量反查 reference_image，老响应才退回 FAISS 行号；
 * 以库中 file_name / file_path 为权威元数据，库里没有才用 CLIP 返回值兜底。
 * 3. FAISS 行号在 Python 侧是字符串，库里是 BIGINT，统一在此处做 String -> Long 转换。
 */
@Slf4j
@Service
public class ImageService {

    private final WebClient clipWebClient;
    private final WebClient duplicatesWebClient;
    private final ImageRepository imageRepository;

    public ImageService(
            @Qualifier("clipWebClient") WebClient clipWebClient,
            @Qualifier("duplicatesWebClient") WebClient duplicatesWebClient,
            ImageRepository imageRepository) {
        this.clipWebClient = clipWebClient;
        this.duplicatesWebClient = duplicatesWebClient;
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
     * 图库浏览每页展示的图片数量。
     */
    @Value("${image.browse.page-size}")
    private int browsePageSize;

    /**
     * 相似图扫描默认阈值与超时。
     */
    @Value("${image.duplicates.threshold:0.98}")
    private double duplicateThreshold;

    @Value("${image.duplicates.timeout-seconds:300}")
    private long duplicateTimeoutSeconds;

    /**
     * 建议保留项：有来源元数据的副本优先，其次最早入库（id 最小）。
     */
    private static final Comparator<ImageEntity> KEEP_PRIORITY = Comparator
            .comparing((ImageEntity entity) -> !hasSourceMeta(entity))
            .thenComparing(ImageEntity::getId);

    // ---------------------------------------------------------------------
    // 对外接口（searchByText / searchByImage / triggerIndexRebuild 同 _4，略）
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

    /**
     * 触发异步索引重建：Python 立即返回，实际编码在后台执行。
     * 前端随后轮询 {@link #indexRebuildStatus()}。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> triggerIndexRebuild() {
        log.info("触发 CLIP 索引重建");

        Map<String, Object> resp = clipWebClient.post()
                .uri("/index")
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorInterceptor("CLIP /index"))
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(timeoutSeconds));

        log.info("索引重建已受理: {}", resp);
        return resp == null ? Map.of() : resp;
    }

    /**
     * 透传 Python 的索引重建进度。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> indexRebuildStatus() {
        Map<String, Object> resp = clipWebClient.get()
                .uri("/index/status")
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorInterceptor("CLIP /index/status"))
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(timeoutSeconds));

        return resp == null ? Map.of() : resp;
    }

    /**
     * 扫描视觉相似图片并分组。只返回候选，是否保留/删除由人工决定。
     */
    public DuplicateReviewResponse findDuplicates(Double requestedThreshold) {
        double threshold = requestedThreshold == null ? duplicateThreshold : requestedThreshold;
        if (threshold < 0.90 || threshold > 0.999) {
            throw new IllegalArgumentException("相似度阈值需在 0.90 到 0.999 之间");
        }

        long start = System.currentTimeMillis();
        log.info("相似图扫描: threshold={}", threshold);

        DuplicateScanResponse resp = duplicatesWebClient.get()
                .uri("/duplicates/scan?threshold={threshold}", threshold)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorInterceptor("相似图扫描"))
                .bodyToMono(DuplicateScanResponse.class)
                .block(Duration.ofSeconds(duplicateTimeoutSeconds));

        if (resp == null) {
            return new DuplicateReviewResponse(
                    threshold, 0, false, System.currentTimeMillis() - start, 0, 0, List.of());
        }

        Set<Long> imageIds = resp.groups() == null ? Set.of() : resp.groups().stream()
                .flatMap(group -> group.members().stream())
                .map(DuplicateScanResponse.Member::imageId)
                .collect(Collectors.toSet());
        Map<Long, ImageEntity> entityById = imageIds.isEmpty()
                ? Map.of()
                : imageRepository.findAllById(imageIds).stream()
                .collect(Collectors.toMap(
                        ImageEntity::getId,
                        Function.identity(),
                        (existing, replacement) -> existing));

        List<DuplicateReviewResponse.Group> groups = new ArrayList<>();
        int totalImages = 0;
        if (resp.groups() != null) {
            for (DuplicateScanResponse.Group raw : resp.groups()) {
                List<ImageEntity> images = new ArrayList<>();
                for (DuplicateScanResponse.Member member : raw.members()) {
                    ImageEntity entity = entityById.get(member.imageId());
                    if (entity == null) {
                        continue;   // 扫描期间被删除
                    }
                    entity.setScore(member.score());
                    images.add(entity);
                }
                if (images.size() < 2) {
                    continue;
                }
                images.sort(Comparator.comparing(ImageEntity::getId));
                Long keepId = images.stream()
                        .min(KEEP_PRIORITY)
                        .map(ImageEntity::getId)
                        .orElse(null);
                groups.add(new DuplicateReviewResponse.Group(
                        images, keepId, raw.minScore(), raw.maxScore()));
                totalImages += images.size();
            }
        }

        groups.sort(Comparator
                .comparingInt((DuplicateReviewResponse.Group group) -> group.images().size())
                .reversed()
                .thenComparing(Comparator.comparingDouble(
                        DuplicateReviewResponse.Group::maxScore).reversed()));

        log.info("相似图扫描完成: scanned={}, groups={}, images={}, cached={}, 耗时={}ms",
                resp.scanned(), groups.size(), totalImages, resp.cached(),
                System.currentTimeMillis() - start);
        return new DuplicateReviewResponse(
                resp.threshold(), resp.scanned(), resp.cached(), resp.elapsedMs(),
                groups.size(), totalImages, groups);
    }

    /**
     * 分页浏览图库全部图片，按 FAISS 行号倒序（最新入库在前）。
     * 页码越界时回落到最后一页，前端因此总能拿到有效数据。
     */
    public ImagePageResponse browseLibrary(int page) {
        int requested = Math.max(page, 1);
        Page<ImageEntity> result = queryBrowsePage(requested);
        if (result.getTotalPages() > 0 && requested > result.getTotalPages()) {
            requested = result.getTotalPages();
            result = queryBrowsePage(requested);
        }
        return new ImagePageResponse(
                result.getContent(),
                requested,
                browsePageSize,
                result.getTotalElements(),
                result.getTotalPages());
    }

    private Page<ImageEntity> queryBrowsePage(int page) {
        return imageRepository.findAllNewestFirst(PageRequest.of(page - 1, browsePageSize));
    }

    // ---------------------------------------------------------------------
    // 私有辅助方法
    // ---------------------------------------------------------------------

    private Function<ClientResponse, Mono<? extends Throwable>> errorInterceptor(String scene) {
        return response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    log.error("{} 调用失败: status={}, body={}",
                            scene, response.statusCode(), body);
                    String message = scene + "服务调用失败: " + response.statusCode();
                    if (!body.isBlank()) {
                        message += " - " + body;
                    }
                    return Mono.error(new IllegalStateException(message));
                });
    }

    private int sizeOf(ClipSearchResponse resp) {
        return resp == null || resp.items() == null ? 0 : resp.items().size();
    }

    private static boolean hasSourceMeta(ImageEntity entity) {
        return !isBlank(entity.getSourceUrl())
                || !isBlank(entity.getSourceId())
                || !isBlank(entity.getPhotographer());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 把 CLIP 返回的结果映射为业务实体。
     * 优先用 imageId（数据库主键）反查：重建/删除会压缩 FAISS 行号，
     * 仅凭行号反查在切换窗口内可能拿到错误图片。老索引没有 imageId 时才退回按行号查。
     * 库中元数据为准，查不到才用 CLIP 返回值兜底；保持 CLIP 返回的原始顺序。
     */
    private List<ImageEntity> toEntities(ClipSearchResponse resp) {
        if (resp == null || resp.items() == null || resp.items().isEmpty()) {
            return List.of();
        }

        List<Long> imageIds = resp.items().stream()
                .map(ClipSearchResponse.ClipItem::imageId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, ImageEntity> entityById = imageIds.isEmpty()
                ? Map.of()
                : imageRepository.findAllById(imageIds).stream()
                .collect(Collectors.toMap(
                        ImageEntity::getId,
                        Function.identity(),
                        (existing, replacement) -> existing));

        List<Long> faissIds = resp.items().stream()
                .map(item -> parseFaissId(item.id()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, ImageEntity> entityByFaissId = faissIds.isEmpty()
                ? Map.of()
                : imageRepository.findAllByFaissIdInAndFaissIdIsNotNull(faissIds).stream()
                .collect(Collectors.toMap(
                        ImageEntity::getFaissId,
                        Function.identity(),
                        (existing, replacement) -> existing));

        List<ImageEntity> result = new ArrayList<>(resp.items().size());
        for (ClipSearchResponse.ClipItem item : resp.items()) {
            Long faissId = parseFaissId(item.id());
            ImageEntity db = item.imageId() != null
                    ? entityById.get(item.imageId())
                    : (faissId == null ? null : entityByFaissId.get(faissId));

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