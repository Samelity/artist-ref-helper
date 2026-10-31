import { javaHttp } from './http'
import type { BatchAddResult, Board, BoardCreate, BoardUpdate } from '@/types/board'

export async function listBoards(): Promise<Board[]> {
  const resp = await javaHttp.get<Board[]>('/api/boards')
  return resp.data
}

export async function getBoard(id: number): Promise<Board> {
  const resp = await javaHttp.get<Board>(`/api/boards/${id}`)
  return resp.data
}

export async function createBoard(data: BoardCreate): Promise<Board> {
  const resp = await javaHttp.post<Board>('/api/boards', data)
  return resp.data
}

export async function updateBoard(id: number, data: BoardUpdate): Promise<Board> {
  const resp = await javaHttp.put<Board>(`/api/boards/${id}`, data)
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

export async function removeImageFromBoard(boardId: number, imageId: number): Promise<Board> {
  const resp = await javaHttp.delete<Board>(`/api/boards/${boardId}/images/${imageId}`)
  return resp.data
}
