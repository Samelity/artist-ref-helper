import { pythonHttp } from './http'

export interface UploadResponse {
  total: number
  message: string
}

export type UploadOutcome = 'saved' | 'duplicate' | 'error'

export interface UploadResultItem {
  name: string
  id: number | null
  outcome: UploadOutcome
}

export interface UploadStatus {
  running: boolean
  batch_id: string | null
  current: number
  total: number
  current_file: string
  saved: number
  skipped: number
  errors: string[]
  skipped_files: string[]
  results: UploadResultItem[]
}

export interface ImportResponse {
  total: number
  message: string
}

export interface ImportStatus {
  running: boolean
  current: number
  total: number
  current_file: string
  imported: number
  skipped: number
  errors: string[]
  skipped_files: string[]
}

export async function uploadImages(files: File[], batchId?: string): Promise<UploadResponse> {
  const fd = new FormData()
  for (const file of files) {
    fd.append('files', file)
  }
  if (batchId) {
    fd.append('batch_id', batchId)
  }
  const resp = await pythonHttp.post<UploadResponse>('/upload', fd, {
    timeout: 120000,
  })
  return resp.data
}

export async function getUploadStatus(): Promise<UploadStatus> {
  const resp = await pythonHttp.get<UploadStatus>('/upload/status')
  return resp.data
}

export interface WaitUploadOptions {
  onProgress?: (status: UploadStatus) => void
  intervalMs?: number
  timeoutMs?: number
}

/** 轮询本批上传直到结束；batchId 用于隔离其他标签页发起的上传任务。 */
export async function waitForUploadResult(
  batchId: string,
  options: WaitUploadOptions = {},
): Promise<UploadStatus> {
  const intervalMs = options.intervalMs ?? 500
  const timeoutMs = options.timeoutMs ?? 5 * 60 * 1000
  const startedAt = Date.now()
  let lastError: unknown = null

  while (true) {
    let status: UploadStatus | null = null
    try {
      status = await getUploadStatus()
    } catch (e) {
      lastError = e
    }

    if (status) {
      if (status.batch_id != null && status.batch_id !== batchId) {
        throw new Error('上传状态已被其他任务覆盖，请到图库确认结果')
      }
      options.onProgress?.(status)
      if (!status.running) return status
    }

    if (Date.now() - startedAt > timeoutMs) {
      const suffix = lastError instanceof Error ? `: ${lastError.message}` : ''
      throw new Error('等待上传结果超时' + suffix)
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs))
  }
}

export async function importFromDirectory(): Promise<ImportResponse> {
  const resp = await pythonHttp.post<ImportResponse>('/import', null, {
    timeout: 30000,
  })
  return resp.data
}

export async function getImportStatus(): Promise<ImportStatus> {
  const resp = await pythonHttp.get<ImportStatus>('/import/status')
  return resp.data
}
