import { Turnstile } from '@marsidev/react-turnstile'
import { AlertCircle, ArrowRight, AudioLines, Clipboard, Download, Link2, ShieldCheck, Video } from 'lucide-react'
import { FormEvent, useEffect, useMemo, useState } from 'react'
import { JobCard } from './components/JobCard'
import { QualityPicker } from './components/QualityPicker'
import { ApiError, cancelDownload, createDownload, getDownload, inspectMedia } from './lib/api'
import { formatDuration } from './lib/format'
import type { DownloadJob, MediaInspection, MediaMode } from './lib/types'

const siteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY

function App() {
  const [url, setUrl] = useState('')
  const [media, setMedia] = useState<MediaInspection | null>(null)
  const [mode, setMode] = useState<MediaMode>('video')
  const [qualityId, setQualityId] = useState('')
  const [rightsConfirmed, setRightsConfirmed] = useState(false)
  const [turnstileToken, setTurnstileToken] = useState(siteKey ? '' : 'development-token')
  const [job, setJob] = useState<DownloadJob | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const options = useMemo(
    () => (mode === 'video' ? media?.video_options ?? [] : media?.audio_options ?? []),
    [media, mode],
  )

  useEffect(() => {
    if (options.length && !options.some((option) => option.id === qualityId)) {
      setQualityId(options.at(-1)?.id ?? '')
    }
  }, [options, qualityId])

  useEffect(() => {
    if (!job || !['queued', 'processing'].includes(job.status)) return
    const timer = window.setTimeout(async () => {
      try {
        setJob(await getDownload(job.job_id))
      } catch (caught) {
        setError(caught instanceof ApiError ? caught.message : 'Não foi possível atualizar o progresso.')
      }
    }, 1500)
    return () => window.clearTimeout(timer)
  }, [job])

  async function handleInspect(event: FormEvent) {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      const result = await inspectMedia(url)
      setMedia(result)
      setMode('video')
      setQualityId(result.video_options.at(-1)?.id ?? '')
    } catch (caught) {
      setMedia(null)
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível analisar o link.')
    } finally {
      setLoading(false)
    }
  }

  async function handleCreate() {
    if (!media || !qualityId) return
    setError('')
    setLoading(true)
    try {
      const created = await createDownload({
        mediaId: media.media_id,
        mode,
        qualityId,
        rightsConfirmed,
        turnstileToken,
      })
      setJob({
        job_id: created.job_id,
        status: created.status,
        progress: 0,
        stage: 'Na fila',
        filename: null,
        download_url: null,
        expires_at: null,
        error: null,
      })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível iniciar o download.')
    } finally {
      setLoading(false)
    }
  }

  async function handleCancel() {
    if (!job) return
    try {
      await cancelDownload(job.job_id)
      setJob({ ...job, status: 'cancelled', stage: 'Download cancelado', progress: 0 })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível cancelar.')
    }
  }

  function reset() {
    setUrl('')
    setMedia(null)
    setMode('video')
    setQualityId('')
    setRightsConfirmed(false)
    setJob(null)
    setError('')
  }

  async function pasteUrl() {
    try {
      setUrl(await navigator.clipboard.readText())
      setError('')
    } catch {
      setError('Permita o acesso à área de transferência ou cole o link manualmente.')
    }
  }

  return (
    <main className="min-h-screen overflow-hidden bg-cream text-ink">
      <div className="ambient ambient-one" />
      <div className="ambient ambient-two" />

      <nav className="relative z-10 mx-auto flex max-w-6xl items-center justify-between px-5 py-6 sm:px-8">
        <button className="flex items-center gap-2" type="button" onClick={reset} aria-label="Voltar ao início">
          <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-forest text-lime">
            <Download size={20} strokeWidth={3} />
          </span>
          <span className="font-display text-xl font-bold tracking-tight">baixaí</span>
        </button>
        <span className="hidden items-center gap-2 rounded-full border border-forest/15 bg-white/50 px-4 py-2 text-xs font-bold text-forest backdrop-blur sm:flex">
          <ShieldCheck size={15} /> Conteúdo autorizado
        </span>
      </nav>

      <div className="relative z-10 mx-auto grid max-w-6xl items-start gap-10 px-5 pb-16 pt-6 sm:px-8 lg:grid-cols-[0.9fr_1.1fr] lg:gap-16 lg:pt-16">
        <section className="pt-4 lg:sticky lg:top-8 lg:pt-8">
          <span className="eyebrow">Simples, rápido e consciente</span>
          <h1 className="mt-5 max-w-xl font-display text-[clamp(3rem,8vw,5.8rem)] font-bold leading-[0.93] tracking-[-0.06em]">
            Sua mídia,
            <span className="relative block text-forest">
              do seu jeito.
              <svg className="absolute -bottom-3 left-0 w-3/4 text-lime" viewBox="0 0 360 18" fill="none" aria-hidden="true">
                <path d="M3 13C90 3 214 3 357 8" stroke="currentColor" strokeWidth="9" strokeLinecap="round" />
              </svg>
            </span>
          </h1>
          <p className="mt-10 max-w-md text-base font-medium leading-7 text-ink/60 sm:text-lg">
            Salve uma cópia dos vídeos e áudios que você criou ou tem permissão para usar. Escolha a qualidade e deixe o resto com a gente.
          </p>
          <div className="mt-8 flex flex-wrap gap-x-6 gap-y-3 text-xs font-bold uppercase tracking-wider text-ink/50">
            <span>Até 30 min</span><span>•</span><span>Até 1 GB</span><span>•</span><span>Link por 1 hora</span>
          </div>
        </section>

        <div>
          {job ? (
            <JobCard job={job} onCancel={handleCancel} onReset={reset} />
          ) : (
            <section className="animate-enter rounded-[2rem] border border-white/70 bg-white/90 p-5 shadow-card backdrop-blur sm:p-8">
              <form onSubmit={handleInspect}>
                <label className="mb-3 block text-sm font-bold" htmlFor="youtube-url">Link do YouTube</label>
                <div className="url-field">
                  <Link2 className="shrink-0 text-forest" size={20} />
                  <input
                    id="youtube-url"
                    type="url"
                    value={url}
                    onChange={(event) => setUrl(event.target.value)}
                    placeholder="https://youtube.com/watch?v=..."
                    required
                    aria-describedby={error ? 'form-error' : undefined}
                  />
                  <button className="paste-button" type="button" onClick={pasteUrl} aria-label="Colar link">
                    <Clipboard size={16} /> <span className="hidden sm:inline">Colar</span>
                  </button>
                </div>
                {!media && (
                  <button className="button-primary mt-4 w-full" disabled={loading || !url} type="submit">
                    {loading ? 'Analisando…' : 'Analisar vídeo'} {!loading && <ArrowRight size={19} />}
                  </button>
                )}
              </form>

              {error && (
                <div id="form-error" role="alert" className="error-box">
                  <AlertCircle size={18} className="shrink-0" /> {error}
                </div>
              )}

              {media && (
                <div className="mt-6 animate-enter border-t border-ink/10 pt-6">
                  <div className="flex gap-4">
                    {media.thumbnail_url && <img className="h-20 w-32 rounded-2xl object-cover" src={media.thumbnail_url} alt="" />}
                    <div className="min-w-0 py-1">
                      <h2 className="line-clamp-2 font-display font-bold leading-snug">{media.title}</h2>
                      <p className="mt-2 text-xs font-bold text-ink/45">{formatDuration(media.duration_seconds)} · YouTube</p>
                    </div>
                  </div>

                  <fieldset className="my-6">
                    <legend className="mb-3 text-sm font-bold">O que você quer salvar?</legend>
                    <div className="mode-switch">
                      <label className={mode === 'video' ? 'mode-active' : ''}>
                        <input className="sr-only" type="radio" name="mode" checked={mode === 'video'} onChange={() => setMode('video')} />
                        <Video size={18} /> Vídeo
                      </label>
                      <label className={mode === 'audio' ? 'mode-active' : ''}>
                        <input className="sr-only" type="radio" name="mode" checked={mode === 'audio'} onChange={() => setMode('audio')} />
                        <AudioLines size={18} /> Áudio
                      </label>
                    </div>
                  </fieldset>

                  <QualityPicker options={options} selected={qualityId} onChange={setQualityId} />

                  <label className="rights-check mt-6">
                    <input type="checkbox" checked={rightsConfirmed} onChange={(event) => setRightsConfirmed(event.target.checked)} />
                    <span>Confirmo que sou titular deste conteúdo ou tenho autorização expressa para baixá-lo.</span>
                  </label>

                  {siteKey && (
                    <div className="mt-5 flex justify-center">
                      <Turnstile siteKey={siteKey} onSuccess={setTurnstileToken} onExpire={() => setTurnstileToken('')} />
                    </div>
                  )}

                  <button
                    className="button-primary mt-6 w-full"
                    type="button"
                    disabled={loading || !rightsConfirmed || !turnstileToken || !qualityId}
                    onClick={handleCreate}
                  >
                    {loading ? 'Criando download…' : 'Preparar arquivo'} {!loading && <Download size={19} />}
                  </button>
                  <button className="mt-4 w-full text-sm font-bold text-ink/45 transition hover:text-ink" type="button" onClick={() => setMedia(null)}>
                    Usar outro link
                  </button>
                </div>
              )}
            </section>
          )}
          <p className="mx-auto mt-6 max-w-lg text-center text-xs leading-5 text-ink/45">
            Ao continuar, você declara possuir os direitos necessários. Este serviço não contorna conteúdo privado, restrições regionais ou controles de acesso.
          </p>
        </div>
      </div>
    </main>
  )
}

export default App
