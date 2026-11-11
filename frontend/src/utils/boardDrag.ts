export const BOARD_DRAG_MIME = 'application/x-artist-image-ids'

export function setBoardDragData(event: DragEvent, imageIds: number[]) {
  const dt = event.dataTransfer
  if (!dt) return
  dt.effectAllowed = 'copy'
  dt.setData(BOARD_DRAG_MIME, JSON.stringify(imageIds))
}

export function hasBoardDragData(event: DragEvent): boolean {
  const types = event.dataTransfer?.types
  return types ? Array.from(types).includes(BOARD_DRAG_MIME) : false
}

export function readBoardDragIds(event: DragEvent): number[] {
  const raw = event.dataTransfer?.getData(BOARD_DRAG_MIME)
  if (!raw) return []
  try {
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (id): id is number => typeof id === 'number' && Number.isInteger(id) && id > 0,
    )
  } catch {
    return []
  }
}
