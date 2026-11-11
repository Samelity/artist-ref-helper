package com.example.artist.service;

import com.example.artist.dto.BoardBatchAddResponse;
import com.example.artist.dto.BoardBatchRemoveResponse;
import com.example.artist.dto.BoardResponse;
import com.example.artist.dto.BoardSummaryResponse;
import com.example.artist.model.BoardEntity;
import com.example.artist.model.BoardImageEntity;
import com.example.artist.model.ImageEntity;
import com.example.artist.repository.BoardImageRepository;
import com.example.artist.repository.BoardRepository;
import com.example.artist.repository.ImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
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
    public BoardSummaryResponse create(BoardEntity board) {
        BoardEntity saved = boardRepository.save(board);
        return toSummary(saved, 0);
    }

    public List<BoardSummaryResponse> listBoards() {
        List<BoardEntity> boards = boardRepository.findAllOrdered();
        if (boards.isEmpty()) {
            return List.of();
        }
        List<Long> boardIds = boards.stream().map(BoardEntity::getId).toList();
        Map<Long, Integer> counts = new HashMap<>();
        for (BoardImageRepository.BoardImageCount row : boardImageRepository.countByBoardIds(boardIds)) {
            counts.put(row.getBoardId(), Math.toIntExact(row.getImageCount()));
        }
        return boards.stream()
                .map(board -> toSummary(board, counts.getOrDefault(board.getId(), 0)))
                .toList();
    }

    public BoardResponse getBoard(Long boardId) {
        BoardEntity board = requireBoard(boardId);
        return toResponse(board, boardImageRepository.findOrderedByBoardId(boardId));
    }

    @Transactional
    public BoardSummaryResponse updateBoard(Long boardId, String name, String description) {
        BoardEntity board = requireBoard(boardId);
        if (name != null) {
            board.setName(name);
        }
        if (description != null) {
            board.setDescription(description);
        }
        BoardEntity saved = boardRepository.save(board);
        return toSummary(saved, Math.toIntExact(boardImageRepository.countByBoardId(boardId)));
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
    public BoardSummaryResponse addImageToBoard(Long boardId, Long imageId) {
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
        }

        int added = toAdd.size();
        int skipped = uniqueIds.size() - added;
        log.info("Batch add to board {}: requested={}, added={}, skipped={}",
                boardId, uniqueIds.size(), added, skipped);
        return new BoardBatchAddResponse(toSummary(board, existingIds.size()), added, skipped);
    }

    @Transactional
    public BoardSummaryResponse removeImageFromBoard(Long boardId, Long imageId) {
        BoardEntity board = requireBoard(boardId);
        boardImageRepository.deleteByBoardIdAndImageId(boardId, imageId);
        log.info("Removed image {} from board {}", imageId, boardId);
        return toSummary(board, Math.toIntExact(boardImageRepository.countByBoardId(boardId)));
    }

    @Transactional
    public BoardBatchRemoveResponse removeImagesFromBoard(Long boardId, List<Long> imageIds) {
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

        BoardEntity board = requireBoard(boardId);
        int removed = boardImageRepository.deleteByBoardIdAndImageIdIn(boardId, uniqueIds);
        log.info("Batch remove from board {}: requested={}, removed={}",
                boardId, uniqueIds.size(), removed);
        return new BoardBatchRemoveResponse(
                toSummary(board, Math.toIntExact(boardImageRepository.countByBoardId(boardId))),
                removed);
    }

    private BoardEntity requireBoard(Long boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found: " + boardId));
    }

    private BoardSummaryResponse toSummary(BoardEntity board, int imageCount) {
        return new BoardSummaryResponse(
                board.getId(),
                board.getName(),
                board.getDescription(),
                board.getCreatedAt(),
                imageCount
        );
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
                board.getCreatedAt(),
                imageResponses.size()
        );
    }
}
