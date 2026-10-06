import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { AlertTriangle, RefreshCw } from 'lucide-react'
import { getDaily } from '../api/client'
import { describeError } from '../api/http'
import { mapDaily } from '../api/mappers'
import DateRangePicker from '../components/DateRangePicker'
import EmptyState from '../components/EmptyState'
import ExpenseDonut from '../components/ExpenseDonut'
import FinanceSection from '../components/FinanceSection'
import ProgressBar from '../components/ProgressBar'
import SummaryCards from '../components/SummaryCards'
import SyncIndicator from '../components/SyncIndicator'
import { MarketplaceLogo, PageHeader } from '../components/ui'
import { useAutoSync } from '../hooks/useAutoSync'
import type { ImportRunner } from '../hooks/useImportRunner'
import type { DailyReport, DateRange, MarketplaceInfo } from '../types'
import { sumDays } from '../utils/format'

const EMPTY_TOTALS = sumDays([])
/** Дней, начиная с которых предупреждаем о большом числе запросов к маркетплейсу.
 *  Сейчас не используется: подтверждение отключено в пользу автозагрузки. */
/* const BIG_IMPORT_DAYS = 45 */

export default function OverviewPage({
  mp,
  range,
  onRangeChange,
  runner,
  onConnect,
}: {
  mp: MarketplaceInfo
  range: DateRange
  onRangeChange: (r: DateRange) => void
  runner: ImportRunner
  onConnect: () => void
}) {
  const [report, setReport] = useState<DailyReport | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const token = useRef(0)

  const reload = useCallback(
    async (silent = false) => {
      const t = ++token.current
      if (!silent) setLoading(true)
      try {
        const r = await getDaily(mp.code, range.from, range.to)
        if (token.current !== t) return
        setReport(r)
        setError(null)
      } catch (e) {
        if (token.current !== t) return
        setError(describeError(e))
      } finally {
        if (token.current === t) setLoading(false)
      }
    },
    [mp.code, range.from, range.to],
  )

  // отчёт при смене маркетплейса / периода
  useEffect(() => {
    if (mp.connected) reload()
  }, [mp.connected, reload])

  // по ходу импорта подтягиваем свежие цифры (результат доступен до окончания импорта)
  const run = runner.run
  const progressKey = run
    ? `${run.id}:${run.doneUnits}:${run.skippedUnits}:${run.failedUnits}:${run.inProgress}`
    : ''
  const lastKey = useRef(progressKey)
  useEffect(() => {
    if (progressKey === lastKey.current) return
    lastKey.current = progressKey
    if (run?.importType === 'FINANCE') reload(true)
  }, [progressKey, run?.importType, reload])

  const days = useMemo(() => (report ? mapDaily(report) : []), [report])
  const totals = useMemo(() => (days.length ? sumDays(days) : EMPTY_TOTALS), [days])

  const startSync = () => {
    // Подтверждение расхода квоты отключено закомментированным блоком ниже:
    // автозагрузка и повторный запуск берут только незагруженные дни,
    // поэтому отдельного диалога не требуется.
    /**const cov = report?.coverage
    const toLoad = cov
      ? cov.missingDays.length + cov.failedDates.length + cov.provisionalDays.length
      : 0
    if (toLoad > BIG_IMPORT_DAYS) {
      const ok = window.confirm(
        `Для загрузки потребуется около ${toLoad} запросов к API маркетплейса, это расходует квоту. Продолжить?`,
      )
      if (!ok) return
    }*/
    runner.startFinance(range.from, range.to)
  }

  const auto = useAutoSync({ mp: mp.connected ? mp.code : null, report, range, runner })

  const header = (
    <PageHeader
      title="Обзор"
      subtitle={
        <>
          Финансовые результаты по вашему магазину на {mp.name}
          <MarketplaceLogo code={mp.code} size="sm" />
        </>
      }
    >
      <div className="flex items-center gap-1.5">
        <SyncIndicator state={auto.state} busy={runner.busy} onRetry={startSync} />
        <DateRangePicker from={range.from} to={range.to} onChange={onRangeChange} />
      </div>
    </PageHeader>
  )

  if (!mp.connected) {
    return (
      <>
        {header}
        <EmptyState mp={mp} onConnect={onConnect} />
      </>
    )
  }

  return (
    <>
{header}
        <ProgressBar runner={runner} showProgress={false} />

      {error && (
        <div className="mb-4 flex items-center gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-[13px] font-semibold text-rose-700">
          <AlertTriangle size={17} className="shrink-0" />
          <span className="flex-1">{error}</span>
          <button
            onClick={() => reload()}
            className="flex items-center gap-1.5 rounded-lg bg-white px-3 py-1.5 text-[12px] font-bold shadow-sm ring-1 ring-rose-200 hover:ring-rose-300"
          >
            <RefreshCw size={13} />
            Повторить
          </button>
        </div>
      )}

      {!report ? (
        !error && (
          <div className="grid gap-4 md:grid-cols-3">
            {[0, 1, 2].map((i) => (
              <div key={i} className="animate-pulse-soft h-[196px] rounded-2xl bg-white/80 ring-1 ring-slate-200/60" />
            ))}
          </div>
        )
      ) : (
        <div className={loading ? 'opacity-60 transition-opacity' : 'transition-opacity'}>
          <div className="space-y-4">
            <SummaryCards totals={totals} />
            <FinanceSection days={days} />
            <ExpenseDonut totals={totals} />
          </div>
        </div>
      )}
    </>
  )
}
