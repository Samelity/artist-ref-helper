package com.example.artist.controller;

import com.example.artist.model.BoardEntity;
import com.example.artist.service.InspirationBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 灵感板接口：收藏、分组、导出。
 */
@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final InspirationBoardService boardService;

    @PostMapping
    public ResponseEntity<BoardEntity> create(@RequestBody BoardEntity board) {
        return ResponseEntity.ok(boardService.create(board));
    }

    @GetMapping
    public ResponseEntity<List<BoardEntity>> list() {
        return ResponseEntity.ok(boardService.listBoards());
    }

    @PostMapping("/{boardId}/images/{imageId}")
    public ResponseEntity<?> addImage(@PathVariable Long boardId, @PathVariable Long imageId) {
        boardService.addImageToBoard(boardId, imageId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{boardId}/export")
    public ResponseEntity<?> export(@PathVariable Long boardId) {
        return ResponseEntity.ok(boardService.exportBoard(boardId));
    }
}