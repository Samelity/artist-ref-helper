import axios from 'axios'

export const javaHttp = axios.create({
  baseURL: import.meta.env.VITE_JAVA_BASE || '',
  timeout: 60000,
})

export const pythonHttp = axios.create({
  baseURL: import.meta.env.VITE_PYTHON_BASE || '',
  timeout: 60000,
})

/** 优先取后端返回的具体原因（Python 的 detail / Java 的 error），避免只显示状态码。 */
export function apiErrorMessage(e: unknown): string {
  if (axios.isAxiosError(e)) {
    const data = e.response?.data as { detail?: unknown; error?: unknown } | undefined
    if (typeof data?.detail === 'string' && data.detail) return data.detail
    if (typeof data?.error === 'string' && data.error) return data.error
    return e.message
  }
  return e instanceof Error ? e.message : String(e)
}
