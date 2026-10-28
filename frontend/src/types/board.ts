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
