import { useCallback, useEffect, useMemo, useState } from 'react'
import { AlertTriangle, RefreshCw } from 'lucide-react'
import { getDaily } from '@/api/client'
import { mapDaily } from '@/api/mappers'
import DateRangePicker from '@/components/common/DateRangePicker'
import ExpenseGroupModal, { ExpenseDetailsModal } from '@/components/finance/ExpenseDetails'
import FinanceSection from '@/components/finance/FinanceSection'
import OverviewFilters from '@/components/finance/OverviewFilters'
import ProgressBar from '@/components/sync/ProgressBar'
import SummaryCards from '@/components/finance/SummaryCards'
import type { SyncState } from '@/components/sync/SyncIndicator'
import { PageHeader } from '@/components/ui'
import { useAutoSync } from '@/features/imports/useAutoSync'
import type { ImportRunner } from '@/features/imports/useImportRunner'
import { importProgressKey, useReportQuery } from '@/features/reports/useReportQuery'
import type { AccountInfo, DateRange, ExpenseCategory } from '@/types'
import { cn } from '@/utils/cn'
import { sumDays } from '@/utils/format'

const EMPTY_TOTALS = sumDays([])

/**
 * Обзор магазина за период.
 *
 * <p>Период, автор и книги стоят одной строкой над отчётом: это условия одного
 * вопроса, и держать их в разных местах — значит заставлять человека искать, что
 * именно применилось. Отбор считается на сервере, поэтому карточки, график и
 * таблица видят одни и те же факты.
 *
 * <p>Выбранные товары хранятся в состоянии страницы, а не в адресе: это взгляд на
 * период, а не отдельная страница, и ссылку на «отбор из трёх книг» никто не
 * пересылает.
 */
export default function OverviewPage({
  account,
  range,
  onRangeChange,
  runner,
  onSyncState,
}: {
  account: AccountInfo
  range: DateRange
  onRangeChange: (r: DateRange) => void
  runner: ImportRunner
  /** Состояние синхронизации для индикатора в верхней панели */
  onSyncState: (state: SyncState | null) => void
}) {
  const [author, setAuthor] = useState('')
  const [skus, setSkus] = useState<number[]>([])
  const [group, setGroup] = useState<ExpenseCategory | null>(null)
  const [detailsOpen, setDetailsOpen] = useState(false)

  const fetchReport = useCallback(
    () =>
      getDaily(account.id, range.from, range.to, {
        author: author || undefined,
        skus: skus.length ? skus : undefined,
      }),
    [account.id, range.from, range.to, author, skus],
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

  const auto = useAutoSync({ accountId: account.id, report, range, runner })

  // Индикатор и кнопка обновления стоят в шапке, рядом с магазином, поэтому
  // состояние уходит наверх. При уходе со страницы его надо погасить: иначе шапка
  // показывала бы статус отчёта, которого на экране больше нет.
  useEffect(() => {
    onSyncState(auto.state)
    return () => onSyncState(null)
  }, [auto.state, onSyncState])

  // Подпись отбора пришла с сервера — по ней видно, что применилось на самом деле.
  // Раскладка расходов появилась в контракте позже, поэтому `?.` на каждом шаге:
  // сервер предыдущей версии её не отдаёт, и страница не должна из-за этого падать.
  const selectedGroup = report?.expenseBreakdown?.groups?.find((g) => g.category === group) ?? null

  return (
    <>
      <PageHeader title="Обзор" />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <DateRangePicker
          from={range.from}
          to={range.to}
          onChange={onRangeChange}
          compact
          align="left"
        />
        <OverviewFilters
          accountId={account.id}
          author={author}
          skus={skus}
          onAuthorChange={setAuthor}
          onSkusChange={setSkus}
          catalogProducts={account.catalogProducts}
        />
      </div>

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
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {[0, 1, 2].map((i) => (
              <div
                key={i}
                className={cn(
                  'animate-pulse-soft h-[168px] rounded-2xl bg-white/80 ring-1 ring-slate-200/60',
                  i === 1 && 'md:order-3 md:col-span-2 xl:order-2',
                  i === 2 && 'md:order-2 xl:order-3',
                )}
              />
            ))}
          </div>
        )
      ) : (
        <div className={loading ? 'opacity-60 transition-opacity' : 'transition-opacity'}>
          <div className="space-y-4">
            <SummaryCards
              totals={totals}
              breakdown={report.expenseBreakdown}
              unallocated={report.unallocatedExpenses}
              scoped={report.scoped}
              payout={report.payout}
              onExpenseGroup={setGroup}
              onExpenseDetails={() => setDetailsOpen(true)}
            />
            <FinanceSection days={days} />
          </div>
        </div>
      )}

      <ExpenseGroupModal
        category={group}
        group={selectedGroup}
        breakdown={report?.expenseBreakdown}
        onClose={() => setGroup(null)}
      />

      <ExpenseDetailsModal
        open={detailsOpen}
        breakdown={report?.expenseBreakdown}
        income={totals.income}
        expenses={totals.expenses}
        onClose={() => setDetailsOpen(false)}
        onGroup={(category) => {
          // Из детализации сразу в расшифровку статьи: иначе пришлось бы закрыть
          // окно, найти карточку расходов и кликнуть там по той же статье.
          setDetailsOpen(false)
          setGroup(category)
        }}
      />
    </>
  )
}
