import { javaHttp, pythonHttp } from './http'
import type { ImageItem, ImagePage } from '@/types/image'

export interface SearchByTextParams {
  query: string
  topK?: number
  threshold?: number
}

export async function searchByText(params: SearchByTextParams): Promise<ImageItem[]> {
  const resp = await javaHttp.get<ImageItem[]>('/api/images/search/text', { params })
  return resp.data
}

export async function searchByImage(file: File, topK?: number, threshold?: number): Promise<ImageItem[]> {
  const fd = new FormData()
  fd.append('file', file)
  const resp = await javaHttp.post<ImageItem[]>('/api/images/search/similar', fd, {
    params: { topK, threshold },
  })
  return resp.data
}

export async function rebuildIndex(): Promise<{ indexedCount: number }> {
  const resp = await javaHttp.post<{ indexedCount: number }>('/api/images/index')
  return resp.data
}

export async function browseImages(page = 1): Promise<ImagePage> {
  const resp = await javaHttp.get<ImagePage>('/api/images/browse', { params: { page } })
  return resp.data
}

export interface DeleteImagesResult {
  deleted: number
  missing: number[]
  errors: string[]
}

/** 删除图库图片：经 /py 代理到 Python 服务，同时删库、删文件并压缩 FAISS。 */
export async function deleteImages(ids: number[]): Promise<DeleteImagesResult> {
  const resp = await pythonHttp.post<DeleteImagesResult>('/images/delete', { ids })
  return resp.data
}
