export function formatScore(score: number | undefined | null): string {
  if (typeof score !== 'number') return '-'
  return (score * 100).toFixed(1) + '%'
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleDateString('zh-CN')
  } catch {
    return ''
  }
}
