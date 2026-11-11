import { javaHttp } from './http'
import type {
  BatchAddResult,
  BatchRemoveResult,
  Board,
  BoardCreate,
  BoardSummary,
  BoardUpdate,
} from '@/types/board'

export async function listBoards(): Promise<BoardSummary[]> {
  const resp = await javaHttp.get<BoardSummary[]>('/api/boards')
  return resp.data
}

export async function getBoard(id: number): Promise<Board> {
  const resp = await javaHttp.get<Board>(`/api/boards/${id}`)
  return resp.data
}

export async function createBoard(data: BoardCreate): Promise<BoardSummary> {
  const resp = await javaHttp.post<BoardSummary>('/api/boards', data)
  return resp.data
}

export async function updateBoard(id: number, data: BoardUpdate): Promise<BoardSummary> {
  const resp = await javaHttp.put<BoardSummary>(`/api/boards/${id}`, data)
  return resp.data
}

export async function deleteBoard(id: number): Promise<void> {
  await javaHttp.delete(`/api/boards/${id}`)
}

export async function addImagesToBoard(
  boardId: number,
  imageIds: number[],
): Promise<BatchAddResult> {
  const resp = await javaHttp.post<BatchAddResult>(`/api/boards/${boardId}/images/batch`, {
    imageIds,
  })
  return resp.data
}

export async function removeImageFromBoard(
  boardId: number,
  imageId: number,
): Promise<BoardSummary> {
  const resp = await javaHttp.delete<BoardSummary>(`/api/boards/${boardId}/images/${imageId}`)
  return resp.data
}

export async function removeImagesFromBoard(
  boardId: number,
  imageIds: number[],
): Promise<BatchRemoveResult> {
  const resp = await javaHttp.post<BatchRemoveResult>(
    `/api/boards/${boardId}/images/remove-batch`,
    { imageIds },
  )
  return resp.data
}
