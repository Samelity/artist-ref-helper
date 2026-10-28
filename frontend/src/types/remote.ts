export type RemoteSource = 'pixabay' | 'pexels'

export interface RemotePhoto {
  id: number | string
  thumbnail: string
  url: string
  photographer: string | null
  alt?: string
}

export interface RemoteSearchResponse {
  total_results: number
  photos: RemotePhoto[]
}

export interface RemoteSaveRequest {
  photos: Array<{
    id: number | string
    url: string
    photographer: string | null
    source: RemoteSource
  }>
}

export interface RemoteSaveResponse {
  saved: number
  skipped: number
  errors: unknown[]
}
