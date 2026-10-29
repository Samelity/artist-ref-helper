export interface ImageItem {
  id: number
  fileName: string | null
  filePath: string | null
  faissId: number | null
  score?: number
}

export interface ImagePage {
  items: ImageItem[]
  page: number
  size: number
  total: number
  totalPages: number
}
