import type { ImageItem } from './image'

export interface Board {
  id: number
  name: string
  description: string | null
  createdAt: string | null
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
  board: Board
  added: number
  skipped: number
}
