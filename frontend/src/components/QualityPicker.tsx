import { Check, FileAudio, MonitorPlay } from 'lucide-react'
import { formatBytes } from '../lib/format'
import type { QualityOption } from '../lib/types'

interface QualityPickerProps {
  options: QualityOption[]
  selected: string
  onChange: (id: string) => void
}

export function QualityPicker({ options, selected, onChange }: QualityPickerProps) {
  return (
    <fieldset>
      <legend className="mb-3 text-sm font-bold text-ink">Qualidade do arquivo</legend>
      <div className="grid gap-2 sm:grid-cols-2">
        {options.map((option) => {
          const active = option.id === selected
          return (
            <label
              key={option.id}
              className={`quality-option ${active ? 'quality-option-active' : ''}`}
            >
              <input
                className="sr-only"
                type="radio"
                name="quality"
                value={option.id}
                checked={active}
                onChange={() => onChange(option.id)}
              />
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-cream">
                {option.mode === 'video' ? <MonitorPlay size={19} /> : <FileAudio size={19} />}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block font-bold text-ink">{option.label}</span>
                <span className="block text-xs font-medium text-ink/55">
                  {option.container.toUpperCase()} · {formatBytes(option.estimated_bytes)}
                </span>
              </span>
              <span className={`quality-check ${active ? 'opacity-100' : 'opacity-0'}`}>
                <Check size={14} strokeWidth={3} />
              </span>
            </label>
          )
        })}
      </div>
    </fieldset>
  )
}
