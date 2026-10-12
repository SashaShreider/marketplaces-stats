import { useCallback, useEffect, useMemo, useState } from 'react'
import { Info } from 'lucide-react'
import { getDaily } from '@/api/client'
import { describeError } from '@/api/http'
import DateRangePicker from '@/components/common/DateRangePicker'
import { mapDaily } from '@/api/mappers'
import { PageHeader } from '@/components/ui'
import type { DailyReport, DateRange, MarketplaceInfo } from '@/types'
import { toExternalDay } from '@/preview/adapters'
import { FinanceTable as ExternalFinanceTable } from '@/preview/ExternalFinanceTable'

/**
 * Временная страница: таблица заказов из другого проекта на месте родной.
 *
 * <p>Существует только чтобы посмотреть вид. Данные настоящие — берутся из
 * `analytics/daily` и переводятся адаптером, потому что формы у проектов разные.
 */
export default function PreviewFinanceTablePage({
  mp,
  range,
  onRangeChange,
}: {
  mp: MarketplaceInfo
  range: DateRange
  onRangeChange: (r: DateRange) => void
}) {
  const [report, setReport] = useState<DailyReport | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setReport(await getDaily(mp.code, range.from, range.to))
      setError(null)
    } catch (e) {
      setError(describeError(e))
    } finally {
      setLoading(false)
    }
  }, [mp.code, range.from, range.to])

  useEffect(() => {
    if (mp.connected) reload()
  }, [mp.connected, reload])

  const rows = useMemo(() => (report ? mapDaily(report).map(toExternalDay) : []), [report])

  return (
    <>
      <PageHeader
        title="Предпросмотр: таблица"
        subtitle="Компонент из другого проекта на живых данных"
      >
        <DateRangePicker from={range.from} to={range.to} onChange={onRangeChange} />
      </PageHeader>

      <div className="mb-4 flex items-start gap-2 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-[12.5px] font-medium leading-relaxed text-amber-800">
        <Info size={15} className="mt-0.5 shrink-0" />
        <span>
          Временная страница для оценки вида. Компонент перенесён без правок оформления:
          изменены только импорты и подгонка данных. Расходы переданы положительными —
          в нашем проекте они отрицательные.
        </span>
      </div>

      {error && <div className="mb-4 text-[13px] font-semibold text-rose-600">{error}</div>}

      <div className={loading ? 'opacity-60 transition-opacity' : 'transition-opacity'}>
        <ExternalFinanceTable data={rows} />
      </div>
    </>
  )
}