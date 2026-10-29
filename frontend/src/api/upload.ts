import { pythonHttp } from './http'

export interface UploadResponse {
  total: number
  message: string
}

export interface UploadStatus {
  running: boolean
  current: number
  total: number
  current_file: string
  saved: number
  skipped: number
  errors: string[]
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
}

export async function uploadImages(files: File[]): Promise<UploadResponse> {
  const fd = new FormData()
  for (const file of files) {
    fd.append('files', file)
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
