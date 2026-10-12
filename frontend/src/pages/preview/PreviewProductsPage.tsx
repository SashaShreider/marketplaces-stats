import { useCallback, useEffect, useMemo, useState } from 'react'
import { Info } from 'lucide-react'
import { getProducts } from '@/api/client'
import { describeError } from '@/api/http'
import DateRangePicker from '@/components/common/DateRangePicker'
import { PageHeader } from '@/components/ui'
import type { DateRange, MarketplaceInfo, ProductsReport } from '@/types'
import { toExternalProduct } from '@/preview/adapters'
import type { MarketplaceId } from '@/preview/types'
import { ProductsView as ExternalProductsView } from '@/preview/ExternalProductsView'

/**
 * Временная страница: список товаров из другого проекта.
 *
 * <p>Внешний компонент ждёт поля, которых в нашем API нет: цена, остаток,
 * рейтинг, категория. Название, SKU, выручка и число заказов — настоящие,
 * остальное подставлено в адаптере, чтобы оценить вид таблицы целиком.
 */
export default function PreviewProductsPage({
  mp,
  range,
  onRangeChange,
}: {
  mp: MarketplaceInfo
  range: DateRange
  onRangeChange: (r: DateRange) => void
}) {
  const [report, setReport] = useState<ProductsReport | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setReport(
        await getProducts(mp.code, { dateFrom: range.from, dateTo: range.to, size: 100 }),
      )
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

  const marketplace = (mp.code.toLowerCase() === 'wb' ? 'wb' : 'ozon') as MarketplaceId
  const products = useMemo(
    () => (report?.rows ?? []).map((r) => toExternalProduct(r, marketplace)),
    [report, marketplace],
  )

  return (
    <>
      <PageHeader
        title="Предпросмотр: список товаров"
        subtitle="Компонент из другого проекта на живых данных"
      >
        <DateRangePicker from={range.from} to={range.to} onChange={onRangeChange} />
      </PageHeader>

      <div className="mb-4 flex items-start gap-2 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-[12.5px] font-medium leading-relaxed text-amber-800">
        <Info size={15} className="mt-0.5 shrink-0" />
        <span>
          Временная страница для оценки вида. Название, SKU, выручка и число заказов —
          настоящие. Цена, остаток, рейтинг и категория в нашем API отсутствуют, поэтому
          подставлены правдоподобными значениями: без них таблица выглядела бы пустой.
        </span>
      </div>

      {error && <div className="mb-4 text-[13px] font-semibold text-rose-600">{error}</div>}

      <div className={loading ? 'opacity-60 transition-opacity' : 'transition-opacity'}>
        <ExternalProductsView products={products} currentMarketplaceId={marketplace} />
      </div>
    </>
  )
}