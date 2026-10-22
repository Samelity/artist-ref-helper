package com.example.artist.service;

import com.example.artist.model.BoardEntity;
import com.example.artist.model.ImageEntity;
import com.example.artist.repository.BoardRepository;
import com.example.artist.repository.ImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 灵感板服务：创建分组、收藏图片、导出合集。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InspirationBoardService {

    private final BoardRepository boardRepository;
    private final ImageRepository imageRepository;

    public BoardEntity create(BoardEntity board) {
        return boardRepository.save(board);
    }

    public List<BoardEntity> listBoards() {
        return boardRepository.findAll();
    }

    public void addImageToBoard(Long boardId, Long imageId) {
        BoardEntity board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found: " + boardId));
        ImageEntity image = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageId));
        board.getImages().add(image);
        boardRepository.save(board);
        log.info("Added image {} to board {}", imageId, boardId);
    }

    public String exportBoard(Long boardId) {
        // TODO: 将灵感板中的图片导出为 ZIP 或合集清单
        return "export-url-placeholder";
    }
}