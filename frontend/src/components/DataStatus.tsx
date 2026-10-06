import { motion } from 'framer-motion'
import { AlertTriangle, CheckCircle2, CloudDownload, Database, RefreshCw } from 'lucide-react'
import type { Coverage, ReportStatus } from '../types'
import { fmtDayShort } from '../utils/format'

interface Props {
  status: ReportStatus
  coverage: Coverage
  reconciled?: boolean
  busy: boolean
  onSync: () => void
}

/** Плашка состояния данных по `status` и `coverage` отчёта */
export function DataStatusBanner({ status, coverage, reconciled, busy, onSync }: Props) {
  const provisional = coverage.provisionalDays.length

  type Variant = {
    tone: string
    icon: React.ReactNode
    text: React.ReactNode
    action?: string
  }
  let v: Variant | null = null

  if (status === 'HAS_ERRORS') {
    v = {
      tone: 'border-rose-200 bg-rose-50 text-rose-700',
      icon: <AlertTriangle size={17} className="text-rose-500" />,
      text: (
        <>
          Не удалось загрузить дней: <b>{coverage.failedDays}</b>
          {coverage.failedDates.length > 0 && (
            <span className="font-medium opacity-80">
              {' '}
              ({coverage.failedDates.slice(0, 4).map(fmtDayShort).join(', ')}
              {coverage.failedDates.length > 4 ? '…' : ''})
            </span>
          )}
          . Показаны неполные данные.
        </>
      ),
      action: 'Повторить загрузку',
    }
  } else if (status === 'PARTIAL') {
    v = {
      tone: 'border-amber-200 bg-amber-50 text-amber-800',
      icon: <AlertTriangle size={17} className="text-amber-500" />,
      text: (
        <>
          Показаны неполные данные: загружено <b>{coverage.loadedDays}</b> из{' '}
          <b>{coverage.requestedDays}</b> дней ({Math.round(coverage.percentLoaded)}%). Не
          загруженные дни отмечены на графике.
        </>
      ),
      action: 'Догрузить',
    }
  } else if (status === 'READY' && provisional > 0) {
    v = {
      tone: 'border-sky-200 bg-sky-50 text-sky-800',
      icon: <RefreshCw size={16} className="text-sky-500" />,
      text: (
        <>
          Данные за последние дни ({provisional}) ещё могут уточниться — маркетплейс досчитывает
          возвраты и корректировки.
        </>
      ),
      action: 'Обновить',
    }
  } else if (status === 'READY') {
    v = {
      tone: 'border-emerald-200/80 bg-emerald-50/70 text-emerald-800',
      icon: <CheckCircle2 size={17} className="text-emerald-500" />,
      text: (
        <>
          Все данные за период загружены
          {coverage.fullyFinal ? ' и окончательные' : ''}
          {reconciled ? ' · сверка с кабинетом сошлась' : ''}.
        </>
      ),
    }
  }

  if (!v) return null

  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      className={`mb-4 flex flex-wrap items-center gap-3 rounded-2xl border px-4 py-3 text-[12.5px] font-semibold ${v.tone}`}
    >
      {v.icon}
      <span className="min-w-[200px] flex-1 leading-relaxed">{v.text}</span>
      {v.action && (
        <button
          disabled={busy}
          onClick={onSync}
          className="flex items-center gap-1.5 rounded-lg bg-white px-3 py-1.5 text-[12px] font-bold text-slate-700 shadow-sm ring-1 ring-slate-200 transition-all hover:ring-slate-300 active:scale-95 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <CloudDownload size={14} />
          {v.action}
        </button>
      )}
    </motion.div>
  )
}

/** Большой призыв, когда за период ничего не загружено */
export function NotLoadedHero({
  coverage,
  busy,
  onSync,
}: {
  coverage: Coverage
  busy: boolean
  onSync: () => void
}) {
  const requests = coverage.requestedDays
  return (
    <motion.div
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      className="relative overflow-hidden rounded-2xl border border-slate-200/70 bg-white shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
    >
      <div className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full bg-brand-100/60 blur-3xl" />
      <div className="relative flex flex-col items-center px-6 py-16 text-center">
        <span className="flex h-20 w-20 items-center justify-center rounded-3xl bg-brand-50 text-brand-500 ring-1 ring-brand-100">
          <Database size={34} />
        </span>
        <h3 className="mt-6 text-[19px] font-extrabold tracking-tight text-slate-900">
          За выбранный период данных ещё нет
        </h3>
        <p className="mt-2 max-w-[460px] text-[13px] leading-relaxed text-slate-500">
          Загрузите начисления с маркетплейса — это фоновый процесс, прогресс будет виден сверху.
          Каждый день периода — отдельный запрос к API маркетплейса ({requests}{' '}
          {requests === 1 ? 'день' : requests < 5 ? 'дня' : 'дней'}), поэтому для первой загрузки
          удобно начать с небольшого диапазона.
        </p>
        <button
          disabled={busy}
          onClick={onSync}
          className="mt-6 flex items-center gap-2 rounded-xl bg-brand-600 px-5 py-3 text-[13.5px] font-bold text-white shadow-lg shadow-brand-600/30 transition-all hover:bg-brand-700 active:scale-95 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <CloudDownload size={16} />
          Загрузить данные за период
        </button>
      </div>
    </motion.div>
  )
}
