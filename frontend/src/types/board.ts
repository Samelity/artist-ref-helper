import type { ImageItem } from './image'

export interface BoardSummary {
  id: number
  name: string
  description: string | null
  createdAt: string | null
  imageCount: number
}

export interface Board extends BoardSummary {
  images: ImageItem[]
}

export interface BoardCreate {
  name: string
  description?: string
}

export interface BoardUpdate {
  name: string
  description: string
}

export interface BatchAddResult {
  board: BoardSummary
  added: number
  skipped: number
}

export interface BatchRemoveResult {
  board: BoardSummary
  removed: number
}
