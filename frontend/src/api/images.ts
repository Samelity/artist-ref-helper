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

export interface IndexRebuildStatus {
  running: boolean
  processed: number
  total: number
  encoded: number
  reused: number
  skipped: number
  errors: string[]
  error: string | null
  started_at: string | null
  finished_at: string | null
}

/** 触发异步索引重建；Python 立即返回，进度通过 getIndexStatus 轮询。 */
export async function rebuildIndex(): Promise<{ accepted?: boolean; message?: string }> {
  const resp = await javaHttp.post<{ accepted?: boolean; message?: string }>('/api/images/index')
  return resp.data
}

export async function getIndexStatus(): Promise<IndexRebuildStatus> {
  const resp = await javaHttp.get<IndexRebuildStatus>('/api/images/index/status')
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

export interface DuplicateImage {
  id: number
  faissId: number | null
  fileName: string | null
  filePath: string | null
  source?: string | null
  sourceId?: string | null
  sourceUrl?: string | null
  photographer?: string | null
  createdAt?: string | null
  /** 与组内最相似图片的余弦相似度 */
  score: number
}

export interface DuplicateGroup {
  images: DuplicateImage[]
  suggestedKeepId: number | null
  minScore: number
  maxScore: number
}

export interface DuplicateScanResult {
  threshold: number
  scanned: number
  cached: boolean
  elapsedMs: number
  totalGroups: number
  totalImages: number
  groups: DuplicateGroup[]
}

/** 扫描视觉相似图片；同步接口，图库较大时耗时较久。 */
export async function scanDuplicates(threshold?: number): Promise<DuplicateScanResult> {
  // 需大于后端 image.duplicates.timeout-seconds（默认 300s），避免浏览器先超时
  const resp = await javaHttp.get<DuplicateScanResult>('/api/images/duplicates', {
    params: { threshold },
    timeout: 320000,
  })
  return resp.data
}
