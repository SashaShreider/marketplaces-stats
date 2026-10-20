import { useCallback, useMemo } from 'react'
import { AlertTriangle, RefreshCw } from 'lucide-react'
import { getDaily } from '@/api/client'
import { mapDaily } from '@/api/mappers'
import DateRangePicker from '@/components/common/DateRangePicker'
import ExpenseDonut from '@/components/finance/ExpenseDonut'
import FinanceSection from '@/components/finance/FinanceSection'
import ProgressBar from '@/components/sync/ProgressBar'
import SummaryCards from '@/components/finance/SummaryCards'
import SyncIndicator from '@/components/sync/SyncIndicator'
import { MarketplaceLogo, PageHeader } from '@/components/ui'
import { useAutoSync } from '@/features/imports/useAutoSync'
import type { ImportRunner } from '@/features/imports/useImportRunner'
import { importProgressKey, useReportQuery } from '@/features/reports/useReportQuery'
import type { AccountInfo, DateRange } from '@/types'
import { sumDays } from '@/utils/format'
import { shopLabel } from '@/utils/marketplace'

const EMPTY_TOTALS = sumDays([])

export default function OverviewPage({
  account,
  range,
  onRangeChange,
  runner,
}: {
  account: AccountInfo
  range: DateRange
  onRangeChange: (r: DateRange) => void
  runner: ImportRunner
}) {
  const fetchReport = useCallback(
    () => getDaily(account.id, range.from, range.to),
    [account.id, range.from, range.to],
  )

  // по ходу импорта подтягиваем свежие цифры (результат доступен до окончания импорта).
  // Каталог на дневной отчёт не влияет, поэтому следим только за финансовым прогоном.
  const run = runner.run
  const progressKey = run?.importType === 'FINANCE' ? importProgressKey(run) : ''

  const { data: report, loading, error, reload } = useReportQuery({
    fetch: fetchReport,
    progressKey,
  })

  const days = useMemo(() => (report ? mapDaily(report) : []), [report])
  const totals = useMemo(() => (days.length ? sumDays(days) : EMPTY_TOTALS), [days])

  const startSync = () => {
    runner.startFinance(range.from, range.to)
  }

  const auto = useAutoSync({ accountId: account.id, report, range, runner })

  const header = (
    <PageHeader
      title="Обзор"
      subtitle={
        <>
          Финансовые результаты магазина {shopLabel(account)}
          <MarketplaceLogo code={account.marketplaceCode} size="sm" />
        </>
      }
    >
      <div className="flex items-center gap-1.5">
        <SyncIndicator state={auto.state} busy={runner.busy} onRetry={startSync} />
        <DateRangePicker from={range.from} to={range.to} onChange={onRangeChange} />
      </div>
    </PageHeader>
  )

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
