import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as boardsApi from '@/api/boards'
import type { BatchAddResult, Board, BoardCreate, BoardUpdate } from '@/types/board'

export const useBoardsStore = defineStore('boards', () => {
  const boards = ref<Board[]>([])
  const currentBoardId = ref<number | null>(null)
  const currentBoard = computed(() =>
    boards.value.find((b) => b.id === currentBoardId.value) ?? null,
  )

  async function load() {
    boards.value = await boardsApi.listBoards()
  }

  async function refreshCurrent() {
    if (currentBoardId.value == null) return
    const fresh = await boardsApi.getBoard(currentBoardId.value)
    const idx = boards.value.findIndex((b) => b.id === fresh.id)
    if (idx >= 0) boards.value[idx] = fresh
  }

  async function create(data: BoardCreate) {
    const b = await boardsApi.createBoard(data)
    boards.value.push(b)
    return b
  }

  async function update(id: number, data: BoardUpdate) {
    const b = await boardsApi.updateBoard(id, data)
    const idx = boards.value.findIndex((x) => x.id === b.id)
    if (idx >= 0) boards.value[idx] = b
    return b
  }

  async function remove(id: number) {
    await boardsApi.deleteBoard(id)
    boards.value = boards.value.filter((b) => b.id !== id)
    if (currentBoardId.value === id) currentBoardId.value = null
  }

  async function addImages(boardId: number, imageIds: number[]): Promise<BatchAddResult> {
    const result = await boardsApi.addImagesToBoard(boardId, imageIds)
    const idx = boards.value.findIndex((b) => b.id === result.board.id)
    if (idx >= 0) boards.value[idx] = result.board
    return result
  }

  async function removeImage(boardId: number, imageId: number) {
    await boardsApi.removeImageFromBoard(boardId, imageId)
    if (currentBoardId.value === boardId) await refreshCurrent()
    await load()
  }

  function select(id: number | null) {
    currentBoardId.value = id
  }

  return {
    boards,
    currentBoardId,
    currentBoard,
    load,
    refreshCurrent,
    create,
    update,
    remove,
    addImages,
    removeImage,
    select,
  }
})
