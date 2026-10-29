const pythonBase = import.meta.env.VITE_PYTHON_BASE || ''

export function imageUrl(filePath: string | null | undefined): string {
  if (!filePath) return ''
  return `${pythonBase}/images/view?path=${encodeURIComponent(filePath)}`
}
