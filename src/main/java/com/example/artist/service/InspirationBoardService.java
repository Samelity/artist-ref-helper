package com.example.artist.service;

import com.example.artist.dto.BoardResponse;
import com.example.artist.model.BoardEntity;
import com.example.artist.model.ImageEntity;
import com.example.artist.repository.BoardRepository;
import com.example.artist.repository.ImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 灵感板服务：创建分组、收藏图片。
 */
@Slf4j
@Service
public class InspirationBoardService {

    private final BoardRepository boardRepository;
    private final ImageRepository imageRepository;

    public InspirationBoardService(
            BoardRepository boardRepository,
            ImageRepository imageRepository) {
        this.boardRepository = boardRepository;
        this.imageRepository = imageRepository;
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
