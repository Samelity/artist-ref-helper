package com.example.artist.service;

import com.example.artist.dto.BoardBatchAddResponse;
import com.example.artist.dto.BoardResponse;
import com.example.artist.model.BoardEntity;
import com.example.artist.model.ImageEntity;
import com.example.artist.repository.BoardRepository;
import com.example.artist.repository.ImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 灵感板服务：创建分组、收藏图片。
 */
@Slf4j
@Service
public class InspirationBoardService {

    private final BoardRepository boardRepository;
    private final ImageRepository imageRepository;
    private final int maxBatchImages;

    public InspirationBoardService(
            BoardRepository boardRepository,
            ImageRepository imageRepository,
            @Value("${board.batch.max-images}") int maxBatchImages) {
        this.boardRepository = boardRepository;
        this.imageRepository = imageRepository;
        this.maxBatchImages = maxBatchImages;
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
        return addImagesToBoard(boardId, List.of(imageId)).board();
    }

    @Transactional
    public BoardBatchAddResponse addImagesToBoard(Long boardId, List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一张图片");
        }

        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long imageId : imageIds) {
            if (imageId != null) {
                uniqueIds.add(imageId);
            }
        }
        if (uniqueIds.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一张图片");
        }
        if (uniqueIds.size() > maxBatchImages) {
            throw new IllegalArgumentException("单次最多添加 " + maxBatchImages + " 张图片");
        }

        BoardEntity board = boardRepository.findByIdWithImages(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found: " + boardId));

        Set<Long> existingIds = new HashSet<>();
        for (ImageEntity image : board.getImages()) {
            existingIds.add(image.getId());
        }

        int added = 0;
        List<ImageEntity> images = imageRepository.findAllById(uniqueIds);
        for (ImageEntity image : images) {
            if (existingIds.add(image.getId())) {
                board.getImages().add(image);
                added++;
            }
        }
        if (added > 0) {
            boardRepository.save(board);
        }

        int skipped = uniqueIds.size() - added;
        log.info("Batch add to board {}: requested={}, added={}, skipped={}",
                boardId, uniqueIds.size(), added, skipped);
        return new BoardBatchAddResponse(toResponse(board), added, skipped);
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
