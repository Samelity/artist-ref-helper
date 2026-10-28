import { javaHttp } from './http'
import type { Board, BoardCreate } from '@/types/board'

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

export async function deleteBoard(id: number): Promise<void> {
  await javaHttp.delete(`/api/boards/${id}`)
}

export async function addImageToBoard(boardId: number, imageId: number): Promise<Board> {
  const resp = await javaHttp.post<Board>(`/api/boards/${boardId}/images/${imageId}`)
  return resp.data
}

export async function removeImageFromBoard(boardId: number, imageId: number): Promise<Board> {
  const resp = await javaHttp.delete<Board>(`/api/boards/${boardId}/images/${imageId}`)
  return resp.data
}

export async function exportBoard(boardId: number): Promise<Blob> {
  const resp = await javaHttp.get(`/api/boards/${boardId}/export`, { responseType: 'blob' })
  return resp.data
}
