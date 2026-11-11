import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as boardsApi from '@/api/boards'
import type {
  BatchAddResult,
  BatchRemoveResult,
  Board,
  BoardCreate,
  BoardSummary,
  BoardUpdate,
} from '@/types/board'

export const useBoardsStore = defineStore('boards', () => {
  const boards = ref<BoardSummary[]>([])
  const currentBoardId = ref<number | null>(null)
  const currentBoard = ref<Board | null>(null)
  const detailLoading = ref(false)
  let detailSeq = 0
  let loadingId: number | null = null

  function toSummary(board: BoardSummary): BoardSummary {
    return {
      id: board.id,
      name: board.name,
      description: board.description,
      createdAt: board.createdAt,
      imageCount: board.imageCount,
    }
  }

  function syncSummary(board: BoardSummary) {
    const summary = toSummary(board)
    const idx = boards.value.findIndex((b) => b.id === summary.id)
    if (idx >= 0) boards.value[idx] = summary
  }

  async function load() {
    boards.value = await boardsApi.listBoards()
  }

  async function select(id: number | null, force = false) {
    if (id == null) {
      detailSeq++
      currentBoardId.value = null
      currentBoard.value = null
      detailLoading.value = false
      return
    }
    if (!force && currentBoardId.value === id && (currentBoard.value?.id === id || loadingId === id)) {
      return
    }
    currentBoardId.value = id
    if (currentBoard.value?.id !== id) currentBoard.value = null
    detailLoading.value = true
    const seq = ++detailSeq
    loadingId = id
    try {
      const detail = await boardsApi.getBoard(id)
      if (seq !== detailSeq || currentBoardId.value !== id) return
      currentBoard.value = detail
      syncSummary(detail)
    } catch {
      if (seq === detailSeq) currentBoard.value = null
    } finally {
      if (seq === detailSeq) {
        detailLoading.value = false
        loadingId = null
      }
    }
  }

  async function refreshCurrent() {
    if (currentBoardId.value == null) return
    await select(currentBoardId.value, true)
  }

  async function create(data: BoardCreate) {
    const b = await boardsApi.createBoard(data)
    boards.value.push(b)
    return b
  }

  async function update(id: number, data: BoardUpdate) {
    const b = await boardsApi.updateBoard(id, data)
    syncSummary(b)
    if (currentBoard.value?.id === id) {
      currentBoard.value = {
        ...currentBoard.value,
        name: b.name,
        description: b.description,
        imageCount: b.imageCount,
      }
    }
    return b
  }

  async function remove(id: number) {
    await boardsApi.deleteBoard(id)
    boards.value = boards.value.filter((b) => b.id !== id)
    if (currentBoardId.value === id) {
      detailSeq++
      currentBoardId.value = null
      currentBoard.value = null
      detailLoading.value = false
    }
  }

  async function addImages(boardId: number, imageIds: number[]): Promise<BatchAddResult> {
    const result = await boardsApi.addImagesToBoard(boardId, imageIds)
    syncSummary(result.board)
    if (currentBoardId.value === boardId) await refreshCurrent()
    return result
  }

  async function removeImage(boardId: number, imageId: number) {
    const summary = await boardsApi.removeImageFromBoard(boardId, imageId)
    syncSummary(summary)
    if (currentBoard.value?.id === boardId) {
      currentBoard.value = {
        ...currentBoard.value,
        imageCount: summary.imageCount,
        images: currentBoard.value.images.filter((img) => img.id !== imageId),
      }
    }
  }

  async function removeImages(boardId: number, imageIds: number[]): Promise<BatchRemoveResult> {
    const result = await boardsApi.removeImagesFromBoard(boardId, imageIds)
    syncSummary(result.board)
    if (currentBoard.value?.id === boardId) {
      const removedIds = new Set(imageIds)
      currentBoard.value = {
        ...currentBoard.value,
        imageCount: result.board.imageCount,
        images: currentBoard.value.images.filter((img) => !removedIds.has(img.id)),
      }
    }
    return result
  }

  return {
    boards,
    currentBoardId,
    currentBoard,
    detailLoading,
    load,
    select,
    refreshCurrent,
    create,
    update,
    remove,
    addImages,
    removeImage,
    removeImages,
  }
})
