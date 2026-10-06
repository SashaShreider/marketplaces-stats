import { AnimatePresence, motion } from 'framer-motion'
import { AlertTriangle, Loader2, X } from 'lucide-react'
import type { ImportRunner } from '../hooks/useImportRunner'
import { fmtDayShort, fmtRange } from '../utils/format'

/**
 * Прогресс фонового импорта (поля ImportProgress: percent, doneUnits, totalUnits…)
 * + уведомление о неудаче импорта.
 */
export default function ProgressBar({ runner }: { runner: ImportRunner }) {
  const { run, error, dismiss } = runner
  const active = Boolean(run?.inProgress)
  const failedMsg =
    error ??
    (run && !run.inProgress && run.status === 'FAILED'
      ? `Импорт завершился с ошибкой${run.error ? `: ${run.error}` : ''}${run.failedUnits ? ` · не удалось: ${run.failedUnits}` : ''}`
      : null)

  const isCatalog = run?.importType === 'CATALOG'
  const handled = run ? run.doneUnits + run.skippedUnits + run.failedUnits : 0
  const pct = run ? Math.round(run.percent) : 0

  return (
    <AnimatePresence initial={false}>
      {active && run && (
        <motion.div
          key="progress"
          initial={{ opacity: 0, height: 0, marginBottom: 0 }}
          animate={{ opacity: 1, height: 'auto', marginBottom: 16 }}
          exit={{ opacity: 0, height: 0, marginBottom: 0 }}
          transition={{ duration: 0.25, ease: 'easeOut' }}
          className="overflow-hidden"
        >
          <div className="flex flex-wrap items-center gap-x-5 gap-y-2 rounded-2xl border border-brand-100 bg-gradient-to-r from-brand-50/90 to-white px-5 py-3.5">
            <Loader2 size={17} className="animate-spin text-brand-600" />
            <span className="text-[13px] font-semibold text-slate-600">
              {isCatalog
                ? 'Загружаем каталог товаров…'
                : run.dateFrom && run.dateTo
                  ? `Загружаем данные за ${fmtRange(run.dateFrom, run.dateTo)}…`
                  : 'Загружаем данные за выбранный период…'}
            </span>
            <div className="h-2 min-w-[120px] flex-1 overflow-hidden rounded-full bg-brand-100">
              <div
                className="h-full rounded-full bg-gradient-to-r from-brand-500 to-brand-600 transition-[width] duration-500 ease-out"
                style={{ width: `${Math.max(2, pct)}%` }}
              />
            </div>
            <span className="tnum text-[12px] font-semibold text-slate-400">
              {handled} из {run.totalUnits} {isCatalog ? 'товаров' : 'дней'}
              {!isCatalog && run.currentUnit && /^\d{4}-\d{2}-\d{2}$/.test(run.currentUnit)
                ? ` · ${fmtDayShort(run.currentUnit)}`
                : ''}
            </span>
            <span className="tnum text-[12px] font-extrabold text-brand-700">{pct}%</span>
          </div>
        </motion.div>
      )}

      {!active && failedMsg && (
        <motion.div
          key="failed"
          initial={{ opacity: 0, height: 0, marginBottom: 0 }}
          animate={{ opacity: 1, height: 'auto', marginBottom: 16 }}
          exit={{ opacity: 0, height: 0, marginBottom: 0 }}
          className="overflow-hidden"
        >
          <div className="flex items-start gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-5 py-3.5">
            <AlertTriangle size={17} className="mt-0.5 shrink-0 text-rose-500" />
            <span className="flex-1 text-[13px] font-semibold text-rose-700">{failedMsg}</span>
            <button
              onClick={dismiss}
              className="flex h-6 w-6 items-center justify-center rounded-md text-rose-400 transition-colors hover:bg-rose-100"
            >
              <X size={14} />
            </button>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
