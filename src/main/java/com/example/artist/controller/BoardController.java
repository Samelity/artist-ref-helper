package com.example.artist.controller;

import com.example.artist.dto.BoardResponse;
import com.example.artist.model.BoardEntity;
import com.example.artist.service.InspirationBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

/**
 * 灵感板接口：收藏、分组、导出。
 */
@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final InspirationBoardService boardService;

    @PostMapping
    public ResponseEntity<BoardResponse> create(@RequestBody BoardEntity board) {
        return ResponseEntity.ok(boardService.create(board));
    }

    @GetMapping
    public ResponseEntity<List<BoardResponse>> list() {
        return ResponseEntity.ok(boardService.listBoards());
    }

    @GetMapping("/{boardId}")
    public ResponseEntity<BoardResponse> get(@PathVariable Long boardId) {
        return ResponseEntity.ok(boardService.getBoard(boardId));
    }

    @PutMapping("/{boardId}")
    public ResponseEntity<BoardResponse> update(@PathVariable Long boardId,
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
    public ResponseEntity<BoardResponse> addImage(@PathVariable Long boardId,
                                                   @PathVariable Long imageId) {
        return ResponseEntity.ok(boardService.addImageToBoard(boardId, imageId));
    }

    @DeleteMapping("/{boardId}/images/{imageId}")
    public ResponseEntity<BoardResponse> removeImage(@PathVariable Long boardId,
                                                      @PathVariable Long imageId) {
        return ResponseEntity.ok(boardService.removeImageFromBoard(boardId, imageId));
    }

    @GetMapping("/{boardId}/export")
    public ResponseEntity<StreamingResponseBody> export(@PathVariable Long boardId) {
        boardService.assertBoardExists(boardId);
        StreamingResponseBody body = outputStream -> boardService.writeExportTo(boardId, outputStream);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"board-" + boardId + ".zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }
}
