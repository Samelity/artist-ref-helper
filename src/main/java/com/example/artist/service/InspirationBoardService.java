package com.example.artist.service;

import com.example.artist.dto.BoardResponse;
import com.example.artist.model.BoardEntity;
import com.example.artist.model.ImageEntity;
import com.example.artist.repository.BoardRepository;
import com.example.artist.repository.ImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 灵感板服务：创建分组、收藏图片、导出合集。
 */
@Slf4j
@Service
public class InspirationBoardService {

    private final BoardRepository boardRepository;
    private final ImageRepository imageRepository;
    private final HttpClient exportHttpClient;
    private final String baseUrl;
    private final long exportTimeoutSeconds;

    public InspirationBoardService(
            BoardRepository boardRepository,
            ImageRepository imageRepository,
            @Value("${clip.service.base-url}") String baseUrl,
            @Value("${clip.service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${board.export.timeout-seconds}") long exportTimeoutSeconds) {
        this.boardRepository = boardRepository;
        this.imageRepository = imageRepository;
        this.baseUrl = baseUrl;
        this.exportTimeoutSeconds = exportTimeoutSeconds;
        this.exportHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    @Transactional
    public BoardResponse create(BoardEntity board) {
        BoardEntity saved = boardRepository.save(board);
        return toResponse(saved);
    }

    public List<BoardResponse> listBoards() {
        return boardRepository.findAllWithImages().stream()
                .map(this::toResponse)
                .toList();
    }

    public BoardResponse getBoard(Long boardId) {
        BoardEntity board = boardRepository.findByIdWithImages(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found: " + boardId));
        return toResponse(board);
    }

    @Transactional
    public BoardResponse updateBoard(Long boardId, String name, String description) {
        BoardEntity board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found: " + boardId));
        if (name != null) {
            board.setName(name);
        }
        if (description != null) {
            board.setDescription(description);
        }
        return toResponse(boardRepository.save(board));
    }

    @Transactional
    public void deleteBoard(Long boardId) {
        if (!boardRepository.existsById(boardId)) {
            throw new IllegalArgumentException("Board not found: " + boardId);
        }
        boardRepository.deleteById(boardId);
        log.info("Deleted board {}", boardId);
    }

    @Transactional
    public BoardResponse addImageToBoard(Long boardId, Long imageId) {
        BoardEntity board = boardRepository.findByIdWithImages(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found: " + boardId));
        ImageEntity image = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageId));

        boolean alreadyPresent = board.getImages().stream()
                .anyMatch(img -> img.getId().equals(imageId));
        if (alreadyPresent) {
            log.info("Image {} already in board {}, skipping", imageId, boardId);
            return toResponse(board);
        }

        board.getImages().add(image);
        boardRepository.save(board);
        log.info("Added image {} to board {}", imageId, boardId);
        return toResponse(board);
    }

    @Transactional
    public BoardResponse removeImageFromBoard(Long boardId, Long imageId) {
        BoardEntity board = boardRepository.findByIdWithImages(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found: " + boardId));
        board.getImages().removeIf(img -> img.getId().equals(imageId));
        boardRepository.save(board);
        log.info("Removed image {} from board {}", imageId, boardId);
        return toResponse(board);
    }

    /**
     * 校验灵感板是否存在；导出前先检查，避免流式响应开始后无法返回错误状态码。
     */
    public void assertBoardExists(Long boardId) {
        if (!boardRepository.existsById(boardId)) {
            throw new IllegalArgumentException("Board not found: " + boardId);
        }
    }

    /**
     * 调用 Python 服务生成灵感板图片 ZIP 包，流式写入输出流。
     * 用 JDK HttpClient 的 InputStream 直接拷贝，绕开 WebFlux DataBuffer 引用计数，
     * 既不受 256KB maxInMemorySize 限制，也没有 ByteBuf 泄漏风险。
     */
    public void writeExportTo(Long boardId, OutputStream outputStream) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/boards/" + boardId + "/export"))
                .timeout(Duration.ofSeconds(exportTimeoutSeconds))
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response =
                    exportHttpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String errorBody;
                try (InputStream body = response.body()) {
                    errorBody = new String(body.readAllBytes(), StandardCharsets.UTF_8);
                }
                throw new IllegalStateException(
                        "Python 服务返回 HTTP " + response.statusCode() + ": " + errorBody);
            }
            try (InputStream body = response.body()) {
                body.transferTo(outputStream);
            }
            outputStream.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Board export interrupted for board {}", boardId, e);
            throw new IllegalStateException("导出灵感板中断");
        } catch (Exception e) {
            log.error("Board export failed for board {}", boardId, e);
            throw new IllegalStateException("导出灵感板失败: " + e.getMessage());
        }
    }

    private BoardResponse toResponse(BoardEntity board) {
        List<BoardResponse.BoardImageResponse> imageResponses = board.getImages().stream()
                .map(img -> new BoardResponse.BoardImageResponse(
                        img.getId(),
                        img.getFaissId(),
                        img.getFileName(),
                        img.getFilePath(),
                        img.getSource(),
                        img.getSourceId(),
                        img.getSourceUrl(),
                        img.getPhotographer()
                ))
                .toList();

        return new BoardResponse(
                board.getId(),
                board.getName(),
                board.getDescription(),
                imageResponses,
                board.getCreatedAt()
        );
    }
}
