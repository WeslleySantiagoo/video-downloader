export type MediaMode = 'video' | 'audio'
export type JobStatus = 'queued' | 'processing' | 'completed' | 'failed' | 'cancelled'

export interface QualityOption {
  id: string
  label: string
  mode: MediaMode
  height: number | null
  container: string
  estimated_bytes: number | null
}

export interface MediaInspection {
  media_id: string
  title: string
  thumbnail_url: string | null
  duration_seconds: number
  video_options: QualityOption[]
  audio_options: QualityOption[]
}

export interface DownloadJob {
  job_id: string
  status: JobStatus
  progress: number
  stage: string
  filename: string | null
  download_url: string | null
  expires_at: string | null
  error: { code: string; message: string } | null
}
