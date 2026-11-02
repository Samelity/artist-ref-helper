const MIME_EXT: Record<string, string> = {
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
}

const IMAGE_EXTS = ['.jpg', '.jpeg', '.png', '.webp']

function extOf(name: string): string {
  const dot = name.lastIndexOf('.')
  return dot >= 0 ? name.slice(dot).toLowerCase() : ''
}

function mimeFromExt(ext: string): string {
  if (ext === 'jpg' || ext === 'jpeg') return 'image/jpeg'
  if (ext === 'webp') return 'image/webp'
  return 'image/png'
}

export function isSupportedImage(file: File): boolean {
  return !!MIME_EXT[file.type] || IMAGE_EXTS.includes(extOf(file.name))
}

export function isEditableTarget(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null
  if (!el) return false
  return el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable
}

export function clipTimestamp(date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-` +
    `${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}${String(date.getMilliseconds()).padStart(3, '0')}`
  )
}

/** 从粘贴事件里取受支持的图片；浏览器未提供 MIME 时按扩展名补齐。 */
export function imagesFromPaste(e: ClipboardEvent): File[] {
  const files = Array.from(e.clipboardData?.files ?? []).filter(isSupportedImage)
  return files.map((file) => {
    if (file.type.startsWith('image/')) return file
    const ext = extOf(file.name).slice(1) || 'png'
    return new File([file], file.name || `paste.${ext}`, { type: mimeFromExt(ext) })
  })
}

/** 粘贴进导入暂存区时统一命名，避免剪贴板常见的 image.png 重名。 */
export function normalizePastedImage(file: File, index: number, stamp: string): File {
  const ext = MIME_EXT[file.type] || extOf(file.name).slice(1) || 'png'
  return new File([file], `clip-${stamp}-${index + 1}.${ext}`, {
    type: file.type || mimeFromExt(ext),
  })
}
