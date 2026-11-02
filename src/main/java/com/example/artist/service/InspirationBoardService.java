package com.example.artist.service;

import com.example.artist.dto.BoardBatchAddResponse;
import com.example.artist.dto.BoardResponse;
import com.example.artist.model.BoardEntity;
import com.example.artist.model.BoardImageEntity;
import com.example.artist.model.ImageEntity;
import com.example.artist.repository.BoardImageRepository;
import com.example.artist.repository.BoardRepository;
import com.example.artist.repository.ImageRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 灵感板服务：创建分组、收藏图片。
 */
@Slf4j
@Service
public class InspirationBoardService {

    private final BoardRepository boardRepository;
    private final BoardImageRepository boardImageRepository;
    private final ImageRepository imageRepository;
    private final int maxBatchImages;

    @PersistenceContext
    private EntityManager entityManager;

    public InspirationBoardService(
            BoardRepository boardRepository,
            BoardImageRepository boardImageRepository,
            ImageRepository imageRepository,
            @Value("${board.batch.max-images}") int maxBatchImages) {
        this.boardRepository = boardRepository;
        this.boardImageRepository = boardImageRepository;
        this.imageRepository = imageRepository;
        this.maxBatchImages = maxBatchImages;
    }

    @Transactional
    public BoardResponse create(BoardEntity board) {
        BoardEntity saved = boardRepository.save(board);
        return toResponse(saved, List.of());
    }

    public List<BoardResponse> listBoards() {
        List<BoardEntity> boards = boardRepository.findAllOrdered();
        if (boards.isEmpty()) {
            return List.of();
        }
        List<Long> boardIds = boards.stream().map(BoardEntity::getId).toList();
        Map<Long, List<BoardImageEntity>> itemsByBoard = new LinkedHashMap<>();
        for (BoardImageEntity item : boardImageRepository.findOrderedByBoardIds(boardIds)) {
            itemsByBoard.computeIfAbsent(item.getBoardId(), key -> new ArrayList<>()).add(item);
        }
        return boards.stream()
                .map(board -> toResponse(board, itemsByBoard.getOrDefault(board.getId(), List.of())))
                .toList();
    }

    public BoardResponse getBoard(Long boardId) {
        BoardEntity board = requireBoard(boardId);
        return toResponse(board, boardImageRepository.findOrderedByBoardId(boardId));
    }

    @Transactional
    public BoardResponse updateBoard(Long boardId, String name, String description) {
        BoardEntity board = requireBoard(boardId);
        if (name != null) {
            board.setName(name);
        }
        if (description != null) {
            board.setDescription(description);
        }
        BoardEntity saved = boardRepository.save(board);
        return toResponse(saved, boardImageRepository.findOrderedByBoardId(boardId));
    }

    @Transactional
    public void deleteBoard(Long boardId) {
        requireBoard(boardId);
        // board_images 对 boards 有外键约束，必须先删关联再删板子
        boardImageRepository.deleteByBoardId(boardId);
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

        BoardEntity board = requireBoard(boardId);
        Set<Long> existingIds = boardImageRepository.findImageIdsByBoardId(boardId);

        List<BoardImageEntity> toAdd = new ArrayList<>();
        for (ImageEntity image : imageRepository.findAllById(uniqueIds)) {
            if (existingIds.add(image.getId())) {
                toAdd.add(new BoardImageEntity(boardId, image.getId()));
            }
        }
        if (!toAdd.isEmpty()) {
            boardImageRepository.saveAll(toAdd);
            // 先落库再把新实例摘出持久化上下文：否则下面的查询会直接复用这些实例
            // （image 未加载），而不是从库里重新取 JOIN FETCH 的结果，toResponse 会拿到 null
            entityManager.flush();
            toAdd.forEach(entityManager::detach);
        }

        int added = toAdd.size();
        int skipped = uniqueIds.size() - added;
        log.info("Batch add to board {}: requested={}, added={}, skipped={}",
                boardId, uniqueIds.size(), added, skipped);
        return new BoardBatchAddResponse(
                toResponse(board, boardImageRepository.findOrderedByBoardId(boardId)), added, skipped);
    }

    @Transactional
    public BoardResponse removeImageFromBoard(Long boardId, Long imageId) {
        BoardEntity board = requireBoard(boardId);
        boardImageRepository.deleteByBoardIdAndImageId(boardId, imageId);
        log.info("Removed image {} from board {}", imageId, boardId);
        return toResponse(board, boardImageRepository.findOrderedByBoardId(boardId));
    }

    private BoardEntity requireBoard(Long boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found: " + boardId));
    }

    private BoardResponse toResponse(BoardEntity board, List<BoardImageEntity> items) {
        List<BoardResponse.BoardImageResponse> imageResponses = items.stream()
                .map(item -> item.getImage())
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
