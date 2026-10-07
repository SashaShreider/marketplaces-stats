import { AlertTriangle, Loader2, RefreshCw } from 'lucide-react'
import type { ImportProgress } from '@/types'

/**
 * Состояние загрузки для индикатора рядом с календарём.
 *
 * <p>Порядок от важного к менее важному: сначала идёт загрузка (пользователь ждёт
 * результат), затем ошибки (данные неполны и надо понимать почему), и лишь потом
 * неокончательные дни — их уточнение это рутина, а не проблема.
 */
export type SyncState =
  | { kind: 'running'; importType: 'FINANCE' | 'CATALOG'; done: number; total: number; percent: number }
  | { kind: 'failed'; days: number }
  | { kind: 'provisional'; days: number }

export interface SyncIndicatorProps {
  state: SyncState | null
  busy: boolean
  onRetry: () => void
}

/**
 * Считает состояние из отчёта и текущего прогона.
 *
 * <p>Вынесено отдельной чистой функцией, чтобы обе страницы показывали одинаковый
 * статус, а логика не расходилась между ними. Берём только поля покрытия: у отчёта
 * по товарам своя форма ответа, но нужная здесь часть у обоих одинаковая.
 */
export function syncStateOf(
  report: { coverage: { failedDays: number; provisionalDays: string[] } } | null,
  run: ImportProgress | null,
): SyncState | null {
  if (run?.inProgress) {
    const handled = run.doneUnits + run.skippedUnits + run.failedUnits
    return {
      kind: 'running',
      importType: run.importType,
      done: handled,
      total: run.totalUnits,
      percent: Math.round(run.percent),
    }
  }
  if (!report) return null

  const coverage = report.coverage
  // Ошибки важнее уточнений: пока часть дней не загрузилась, догружать «свежесть»
  // бессмысленно — сначала надо разобраться с причиной.
  if (coverage.failedDays > 0) return { kind: 'failed', days: coverage.failedDays }
  if (coverage.provisionalDays.length > 0) {
    return { kind: 'provisional', days: coverage.provisionalDays.length }
  }
  return null
}

/**
 * Компактный индикатор загрузки рядом с календарём.
 *
 * <p>Раньше эти же сведения показывались широкой плашкой под шапкой, и почти всегда:
 * последние дни почти всегда неокончательные, так что плашка висела постоянно и
 * обесценивала настоящие проблемы. Здесь тот же смысл умещается в один значок,
 * а во время загрузки рядом с датами видно, сколько дней уже забрано.
 */
export default function SyncIndicator({ state, busy, onRetry }: SyncIndicatorProps) {
  if (!state) return null

  if (state.kind === 'running') {
    const isCatalog = state.importType === 'CATALOG'
    const unit = isCatalog
      ? state.total === 1 ? 'товар' : state.total < 5 ? 'товара' : 'товаров'
      : state.total === 1 ? 'день' : state.total < 5 ? 'дня' : 'дней'
    // Итог в скобках, а не вместо прогресса: пока идёт первый заход, важно видеть
    // и «сколько осталось», и «как далеко мы», а по одному проценту масштаб не виден.
    const title = isCatalog ? 'Загружаем каталог товаров' : 'Загружаем данные за выбранный период'
    return (
      <div
        className="flex h-8 items-center gap-2 rounded-lg bg-brand-50 px-2.5 text-brand-700 ring-1 ring-brand-100"
        title={title}
      >
        <Loader2 size={14} className="shrink-0 animate-spin" />
        <span className="tnum text-[12.5px] font-bold whitespace-nowrap">
          {state.done} из {state.total} {unit}
        </span>
        <span className="tnum text-[12px] font-semibold text-brand-400">{state.percent}%</span>
      </div>
    )
  }

  if (state.kind === 'failed') {
    const title =
      state.days === 1
        ? 'Один день не удалось загрузить. Нажмите, чтобы повторить.'
        : `Не удалось загрузить дней: ${state.days}. Нажмите, чтобы повторить.`
    return (
      <button
        type="button"
        onClick={onRetry}
        disabled={busy}
        title={title}
        aria-label={title}
        className="flex h-8 items-center gap-2 rounded-lg bg-rose-50 px-2.5 text-rose-700 ring-1 ring-rose-100 transition-colors hover:bg-rose-100 disabled:cursor-default disabled:opacity-60"
      >
        <AlertTriangle size={14} className="shrink-0 text-rose-500" />
        <span className="text-[12.5px] font-bold whitespace-nowrap">Ошибок: {state.days}</span>
      </button>
    )
  }

  const title =
    state.days === 1
      ? 'Данные за последний день могут уточниться — маркетплейс досчитывает возвраты. Нажмите, чтобы обновить.'
      : `Данные за последние ${state.days} дн. могут уточниться — маркетплейс досчитывает возвраты. Нажмите, чтобы обновить.`
  return (
    <button
      type="button"
      onClick={onRetry}
      disabled={busy}
      title={title}
      aria-label={title}
      className="group flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-sky-50 hover:text-sky-600 disabled:cursor-default disabled:hover:bg-transparent disabled:hover:text-slate-400"
    >
      {busy ? (
        <Loader2 size={15} className="animate-spin text-sky-500" />
      ) : (
        <RefreshCw size={15} className="transition-transform duration-500 group-hover:rotate-90" />
      )}
    </button>
  )
}
