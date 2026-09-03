import { Ban, CheckCircle2, Download, LoaderCircle, RotateCcw } from 'lucide-react'
import type { DownloadJob } from '../lib/types'

interface JobCardProps {
  job: DownloadJob
  onCancel: () => void
  onReset: () => void
}

export function JobCard({ job, onCancel, onReset }: JobCardProps) {
  const active = job.status === 'queued' || job.status === 'processing'
  const completed = job.status === 'completed'

  return (
    <section className="animate-enter rounded-[2rem] border border-ink/10 bg-white p-6 shadow-card sm:p-8" aria-live="polite">
      <div className="mb-6 flex items-start justify-between gap-4">
        <div>
          <span className="eyebrow">Status do arquivo</span>
          <h2 className="mt-2 font-display text-2xl font-bold text-ink">
            {completed ? 'Tudo pronto!' : active ? 'Estamos preparando' : 'Não foi dessa vez'}
          </h2>
        </div>
        <span className={`status-orb ${completed ? 'bg-forest text-lime' : active ? 'bg-lime text-ink' : 'bg-red-100 text-red-700'}`}>
          {completed ? <CheckCircle2 /> : active ? <LoaderCircle className="animate-spin" /> : <Ban />}
        </span>
      </div>

      {active && (
        <>
          <div className="mb-2 flex justify-between text-sm font-bold text-ink">
            <span>{job.stage}</span>
            <span>{job.progress}%</span>
          </div>
          <div className="h-3 overflow-hidden rounded-full bg-cream">
            <div
              className="h-full rounded-full bg-forest transition-all duration-500"
              style={{ width: `${Math.max(job.progress, 2)}%` }}
            />
          </div>
          <p className="mt-4 text-sm leading-relaxed text-ink/55">
            Você pode manter esta página aberta. O andamento é atualizado automaticamente.
          </p>
          <button className="button-secondary mt-6 w-full" type="button" onClick={onCancel}>
            Cancelar
          </button>
        </>
      )}

      {completed && job.download_url && (
        <>
          <p className="mb-6 break-words text-sm text-ink/60">{job.filename}</p>
          <a className="button-primary w-full" href={job.download_url} download>
            <Download size={19} /> Baixar arquivo
          </a>
          <p className="mt-3 text-center text-xs font-semibold text-ink/45">
            Link disponível por até 1 hora
          </p>
          <button className="button-secondary mt-5 w-full" type="button" onClick={onReset}>
            <RotateCcw size={17} /> Baixar outra mídia
          </button>
        </>
      )}

      {!active && !completed && (
        <>
          <p className="rounded-2xl bg-red-50 p-4 text-sm font-medium text-red-800">
            {job.error?.message ?? (job.status === 'cancelled' ? 'O download foi cancelado.' : 'O arquivo não pôde ser processado.')}
          </p>
          <button className="button-secondary mt-5 w-full" type="button" onClick={onReset}>
            <RotateCcw size={17} /> Tentar novamente
          </button>
        </>
      )}
    </section>
  )
}
