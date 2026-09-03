import type { DownloadJob, JobStatus, MediaInspection, MediaMode } from './types'

const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8000').replace(/\/$/, '')

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly code = 'request_failed',
  ) {
    super(message)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_URL}${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init?.headers },
    })
  } catch {
    throw new ApiError('Não foi possível conectar ao servidor. Tente novamente.', 'network_error')
  }
  if (!response.ok) {
    const payload = await response.json().catch(() => null)
    throw new ApiError(payload?.message ?? 'Não foi possível concluir a solicitação.', payload?.code)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export function inspectMedia(url: string): Promise<MediaInspection> {
  return request('/api/v1/media/inspect', { method: 'POST', body: JSON.stringify({ url }) })
}

export function createDownload(input: {
  mediaId: string
  mode: MediaMode
  qualityId: string
  rightsConfirmed: boolean
  turnstileToken: string
}): Promise<{ job_id: string; status: JobStatus }> {
  return request('/api/v1/downloads', {
    method: 'POST',
    body: JSON.stringify({
      media_id: input.mediaId,
      mode: input.mode,
      quality_id: input.qualityId,
      rights_confirmed: input.rightsConfirmed,
      turnstile_token: input.turnstileToken,
    }),
  })
}

export function getDownload(jobId: string): Promise<DownloadJob> {
  return request(`/api/v1/downloads/${jobId}`)
}

export function cancelDownload(jobId: string): Promise<void> {
  return request(`/api/v1/downloads/${jobId}`, { method: 'DELETE' })
}
