package com.example.artist.controller;

import com.example.artist.dto.BoardBatchAddRequest;
import com.example.artist.dto.BoardBatchAddResponse;
import com.example.artist.dto.BoardBatchRemoveRequest;
import com.example.artist.dto.BoardBatchRemoveResponse;
import com.example.artist.dto.BoardResponse;
import com.example.artist.dto.BoardSummaryResponse;
import com.example.artist.model.BoardEntity;
import com.example.artist.service.InspirationBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 灵感板接口：收藏、分组。
 */
@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final InspirationBoardService boardService;

    @PostMapping
    public ResponseEntity<BoardSummaryResponse> create(@RequestBody BoardEntity board) {
        return ResponseEntity.ok(boardService.create(board));
    }

    @GetMapping
    public ResponseEntity<List<BoardSummaryResponse>> list() {
        return ResponseEntity.ok(boardService.listBoards());
    }

    @GetMapping("/{boardId}")
    public ResponseEntity<BoardResponse> get(@PathVariable Long boardId) {
        return ResponseEntity.ok(boardService.getBoard(boardId));
    }

    @PutMapping("/{boardId}")
    public ResponseEntity<BoardSummaryResponse> update(@PathVariable Long boardId,
                                                        @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(boardService.updateBoard(
                boardId, body.get("name"), body.get("description")));
    }

    @DeleteMapping("/{boardId}")
    public ResponseEntity<Void> delete(@PathVariable Long boardId) {
        boardService.deleteBoard(boardId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{boardId}/images/{imageId}")
    public ResponseEntity<BoardSummaryResponse> addImage(@PathVariable Long boardId,
                                                          @PathVariable Long imageId) {
        return ResponseEntity.ok(boardService.addImageToBoard(boardId, imageId));
    }

    @PostMapping("/{boardId}/images/batch")
    public ResponseEntity<BoardBatchAddResponse> addImages(
            @PathVariable Long boardId,
            @RequestBody BoardBatchAddRequest request) {
        return ResponseEntity.ok(boardService.addImagesToBoard(boardId, request.imageIds()));
    }

    @DeleteMapping("/{boardId}/images/{imageId}")
    public ResponseEntity<BoardSummaryResponse> removeImage(@PathVariable Long boardId,
                                                             @PathVariable Long imageId) {
        return ResponseEntity.ok(boardService.removeImageFromBoard(boardId, imageId));
    }

    @PostMapping("/{boardId}/images/remove-batch")
    public ResponseEntity<BoardBatchRemoveResponse> removeImages(
            @PathVariable Long boardId,
            @RequestBody BoardBatchRemoveRequest request) {
        return ResponseEntity.ok(boardService.removeImagesFromBoard(boardId, request.imageIds()));
    }
}
