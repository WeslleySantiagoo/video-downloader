import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import * as api from './lib/api'

vi.mock('./lib/api', async () => {
  const actual = await vi.importActual<typeof import('./lib/api')>('./lib/api')
  return { ...actual, inspectMedia: vi.fn(), createDownload: vi.fn(), getDownload: vi.fn(), cancelDownload: vi.fn() }
})

const media = {
  media_id: 'dQw4w9WgXcQ',
  title: 'Meu vídeo autorizado',
  thumbnail_url: null,
  duration_seconds: 125,
  video_options: [
    { id: 'video-360', label: '360p', mode: 'video' as const, height: 360, container: 'mp4', estimated_bytes: 2_000_000 },
    { id: 'video-1080', label: '1080p', mode: 'video' as const, height: 1080, container: 'webm', estimated_bytes: 9_000_000 },
  ],
  audio_options: [
    { id: 'audio-best', label: 'Melhor qualidade', mode: 'audio' as const, height: null, container: 'mp3', estimated_bytes: 1_000_000 },
  ],
}

describe('App', () => {
  beforeEach(() => vi.clearAllMocks())

  it('analisa a mídia e seleciona a maior qualidade por padrão', async () => {
    vi.mocked(api.inspectMedia).mockResolvedValue(media)
    const user = userEvent.setup()
    render(<App />)

    await user.type(screen.getByLabelText('Link do YouTube'), 'https://youtu.be/dQw4w9WgXcQ')
    await user.click(screen.getByRole('button', { name: 'Analisar vídeo' }))

    expect(await screen.findByText('Meu vídeo autorizado')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /1080p/ })).toBeChecked()
    expect(screen.getByRole('button', { name: /Preparar arquivo/ })).toBeDisabled()
  })

  it('troca para áudio e cria o job após a confirmação', async () => {
    vi.mocked(api.inspectMedia).mockResolvedValue(media)
    vi.mocked(api.createDownload).mockResolvedValue({ job_id: 'job-1', status: 'queued' })
    const user = userEvent.setup()
    render(<App />)

    await user.type(screen.getByLabelText('Link do YouTube'), 'https://youtu.be/dQw4w9WgXcQ')
    await user.click(screen.getByRole('button', { name: 'Analisar vídeo' }))
    await screen.findByText('Meu vídeo autorizado')
    await user.click(screen.getByLabelText('Áudio'))
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: /Preparar arquivo/ }))

    await waitFor(() => expect(api.createDownload).toHaveBeenCalledWith(expect.objectContaining({ mode: 'audio', qualityId: 'audio-best' })))
    expect(await screen.findByText('Estamos preparando')).toBeInTheDocument()
  })
})
