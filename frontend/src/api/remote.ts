import { pythonHttp } from './http'
import type { RemoteSearchResponse, RemoteSaveRequest, RemoteSaveResponse, RemoteSource } from '@/types/remote'

export async function searchRemote(
  source: RemoteSource,
  query: string,
  page: number,
  perPage: number,
): Promise<RemoteSearchResponse> {
  const path = source === 'pixabay' ? '/pixabay/search' : '/pexels/search'
  const resp = await pythonHttp.get<RemoteSearchResponse>(path, {
    params: { query, per_page: perPage, page },
  })
  return resp.data
}

export async function saveRemote(data: RemoteSaveRequest): Promise<RemoteSaveResponse> {
  const resp = await pythonHttp.post<RemoteSaveResponse>('/remote/save', data)
  return resp.data
}
