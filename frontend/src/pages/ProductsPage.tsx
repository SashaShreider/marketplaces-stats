import { useCallback, useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { AlertTriangle, CloudDownload, Package, RefreshCw } from 'lucide-react'
import { getProducts } from '@/api/client'
import DateRangePicker from '@/components/common/DateRangePicker'
import ScopeFilters from '@/components/common/ScopeFilters'
import ProductsTable from '@/components/finance/ProductsTable'
import ProductSalesDynamics from '@/components/finance/ProductSalesDynamics'
import type { SyncState } from '@/components/sync/SyncIndicator'
import { AnimatedNumber, PageHeader, StatCard } from '@/components/ui'
import { useAutoSync } from '@/features/imports/useAutoSync'
import type { ImportRunner } from '@/features/imports/useImportRunner'
import { useUrlState } from '@/features/navigation/urlState'
import { importProgressKey, useReportQuery } from '@/features/reports/useReportQuery'
import type { AccountInfo, DateRange, ProductSort, ProductSortOrder } from '@/types'
import { cn } from '@/utils/cn'
import { fmtMoney, fmtNum, grossProfit } from '@/utils/format'

const PAGE_SIZE = 200

/**
 * Направление по умолчанию для каждого критерия.
 *
 * <p>По деньгам и количествам — от большего: так видно лучших и худших. По имени и
 * SKU — от меньшего: так их и читают.
 */
const NATURAL_ORDER: Record<ProductSort, ProductSortOrder> = {
  INCOME: 'desc',
  SOLD: 'desc',
  EXPENSES: 'desc',
  GROSS: 'desc',
  NAME: 'asc',
  SKU: 'asc',
}

/** Отбор: все товары каталога или только те, у которых были продажи. */
const SALES_FILTERS: { id: boolean; label: string }[] = [
  { id: false, label: 'Все' },
  { id: true, label: 'С продажами' },
]

export default function ProductsPage({
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
  // В адресе — только условия отбора: период (в шапке экрана), автор и товары.
  // Критерий и направление сортировки, «только с продажами» и номер страницы —
  // состояние страницы: это способ прочитать один и тот же отчёт, а не другое
  // условие вопроса.
  const [author, setAuthor] = useUrlState<string>(
    (params) => params.get('author') ?? '',
    (value) => ({ author: value.trim() || null }),
  )
  // Товары те же, что на «Обзоре»: повторяющийся `skus`, плюс `ozon_skus` как
  // синоним, чтобы скопированная из похожего кабинета ссылка открывала тот же отбор.
  const [skus, setSkus] = useUrlState<number[]>(
    (params) =>
      [...params.getAll('skus'), ...params.getAll('ozon_skus')]
        .flatMap((v) => v.split(','))
        .map((v) => Number(v.trim()))
        .filter((n) => Number.isInteger(n) && n > 0),
    (list) => ({ skus: list.map(String), ozon_skus: null }),
  )
  const [sort, setSort] = useState<ProductSort>('INCOME')
  const [order, setOrder] = useState<ProductSortOrder>('desc')
  const [onlyWithSales, setOnlyWithSales] = useState(false)
  const [page, setPage] = useState(0)

  // фильтры и порядок меняются — возвращаемся на первую страницу
  useEffect(() => setPage(0), [author, skus, sort, order, onlyWithSales, range.from, range.to])

  const fetchReport = useCallback(
    () =>
      getProducts(account.id, {
        dateFrom: range.from,
        dateTo: range.to,
        // Товары сильнее автора — то же правило, что на сервере.
        author: skus.length ? undefined : author.trim() || undefined,
        skus: skus.length ? skus : undefined,
        sort,
        order,
        onlyWithSales,
        page,
        size: PAGE_SIZE,
      }),
    [account.id, range.from, range.to, author, skus, sort, order, onlyWithSales, page],
  )

  /**
   * Заголовок таблицы выбирает критерий сортировки.
   *
   * <p>Повторный клик по тому же заголовку переворачивает направление: «по расходам
   * от большего» и «по расходам от меньшего» — это два разных вопроса, и оба
   * спрашивают про один и тот же столбец.
   */
  const onSort = (criterion: ProductSort) => {
    if (criterion === sort) {
      setOrder(order === 'asc' ? 'desc' : 'asc')
    } else {
      setSort(criterion)
      setOrder(NATURAL_ORDER[criterion])
    }
  }

  // обновляем по ходу импортов
  const run = runner.run
  const { data: report, loading, error, reload } = useReportQuery({
    fetch: fetchReport,
    progressKey: importProgressKey(run),
  })

  // Догружаем недостающие дни выбранного периода, как это делает обзор.
  // Без этого страница товаров молча показывала неполные цифры: период в календаре
  // менялся, дни в фоне не грузились, и отчёт выглядел бы готовым при неполных
  // данных. Отчёт по товарам несёт то же покрытие периода, поэтому хуку достаточно.
  const auto = useAutoSync({ accountId: account.id, report, range, runner })

  // Статус синхронизации показывает шапка — она общая для обеих страниц.
  useEffect(() => {
    onSyncState(auto.state)
    return () => onSyncState(null)
  }, [auto.state, onSyncState])

  const header = (
    <PageHeader title="Товары">
      <DateRangePicker from={range.from} to={range.to} onChange={onRangeChange} />
    </PageHeader>
  )

  const catalogMissing = report ? !report.catalog.loaded : false
  const totals = report?.totals
  // Пока итогов нет, число всё равно куда-то попадёт в разметку — держим ноль,
  // чтобы карточка не показывала «NaN» между загрузкой и данными.
  const profit = grossProfit(totals?.income ?? 0, totals?.expenses ?? 0)
  const hasFilter = Boolean(author.trim() || skus.length > 0 || onlyWithSales)

  return (
    <>
      {header}

      {error && (
        <div className="mb-4 flex items-center gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-[13px] font-semibold text-rose-700">
          <AlertTriangle size={17} className="shrink-0" />
          <span className="flex-1">{error}</span>
          <button
            onClick={() => reload()}
            className="flex items-center gap-1.5 rounded-lg bg-white px-3 py-1.5 text-[12px] font-bold shadow-sm ring-1 ring-rose-200"
          >
            <RefreshCw size={13} />
            Повторить
          </button>
        </div>
      )}

      {!report ? (
        !error && <div className="animate-pulse-soft h-[360px] rounded-2xl bg-white/80 ring-1 ring-slate-200/60" />
      ) : catalogMissing ? (
        <motion.div
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          className="relative overflow-hidden rounded-2xl border border-slate-200/70 bg-white"
        >
          <div className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full bg-brand-100/60 blur-3xl" />
          <div className="relative flex flex-col items-center px-6 py-16 text-center">
            <span className="flex h-20 w-20 items-center justify-center rounded-3xl bg-brand-50 text-brand-500 ring-1 ring-brand-100">
              <Package size={34} />
            </span>
            <h3 className="mt-6 text-[19px] font-extrabold tracking-tight text-slate-900">
              Каталог товаров ещё не загружен
            </h3>
            <p className="mt-2 max-w-[440px] text-[13px] leading-relaxed text-slate-500">
              Загрузите каталог, чтобы увидеть названия, авторов, ISBN и обложки. Это делается один раз.
            </p>
            <button
              disabled={runner.busy}
              onClick={() => runner.startCatalog()}
              className="mt-6 flex items-center gap-2 rounded-xl bg-brand-600 px-5 py-3 text-[13.5px] font-bold text-white shadow-lg shadow-brand-600/30 transition-all hover:bg-brand-700 active:scale-95 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <CloudDownload size={16} />
              Загрузить каталог
            </button>
          </div>
        </motion.div>
      ) : (
        <div className={loading ? 'opacity-60 transition-opacity' : 'transition-opacity'}>
          {totals && (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <StatCard
                label="Продажи"
                tone="border-emerald-200/60 bg-emerald-50/60"
                delay={0.05}
                value={<AnimatedNumber value={totals.income} />}
                sub={
                  <span className="flex flex-wrap items-center gap-x-2.5 gap-y-0.5">
                    <span>продано {fmtNum(totals.soldQuantity ?? 0)} шт</span>
                    {(totals.returnedQuantity ?? 0) > 0 && (
                      <span>возвращено {fmtNum(totals.returnedQuantity ?? 0)} шт</span>
                    )}
                  </span>
                }
              />
              <StatCard
                label="Расходы по товарам"
                tone="border-rose-200/60 bg-rose-50/60"
                delay={0.1}
                value={<AnimatedNumber value={totals.expenses} />}
                sub={
                  report.unallocatedExpenses > 0
                    ? `ещё ${fmtMoney(report.unallocatedExpenses)} без привязки к товару`
                    : undefined
                }
              />
              <StatCard
                label="Прибыль"
                tone={profit >= 0 ? 'border-brand-200/60 bg-brand-50/60' : 'border-rose-200/60 bg-rose-50/60'}
                delay={0.1}
                value={<AnimatedNumber value={profit} />}
              />
              <StatCard
                label="Товаров с продажами"
                tone="border-slate-200/80 bg-white"
                delay={0.2}
                value={<AnimatedNumber value={totals.productsWithSales} suffix="" />}
                sub={`из ${fmtNum(totals.productsInCatalog)} в каталоге`}
              />
            </div>
          )}

          <motion.section
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.45, delay: 0.25 }}
            className="mt-4 rounded-2xl border border-slate-200/70 bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
          >
            {/* Панель фильтров — те же списки, что на «Обзоре»: один способ задать
                вопрос «про кого отчёт», а не два разных. */}
            <div className="flex flex-wrap items-center gap-2">
              <ScopeFilters
                accountId={account.id}
                author={author}
                skus={skus}
                onAuthorChange={setAuthor}
                onSkusChange={setSkus}
                catalogProducts={account.catalogProducts}
              />

              <div className="flex rounded-full bg-slate-100 p-1">
                {SALES_FILTERS.map((f) => (
                  <button
                    key={f.id ? 'sold' : 'all'}
                    onClick={() => setOnlyWithSales(f.id)}
                    title={f.id ? 'Только товары, у которых были продажи' : 'Все товары каталога'}
                    className="relative rounded-full px-3.5 py-1.5 text-[12px] font-bold"
                  >
                    {onlyWithSales === f.id && (
                      <motion.span
                        layoutId="sales-pill"
                        className="absolute inset-0 rounded-full bg-white shadow-sm ring-1 ring-slate-200/70"
                        transition={{ type: 'spring', stiffness: 420, damping: 34 }}
                      />
                    )}
                    <span className={cn('relative z-10', onlyWithSales === f.id ? 'text-brand-700' : 'text-slate-500')}>
                      {f.label}
                    </span>
                  </button>
                ))}
              </div>

              <button
                disabled={runner.busy}
                onClick={() => runner.startCatalog()}
                title="Каталог переписывается целиком (1 запрос к маркетплейсу)"
                className="ml-auto flex h-9 items-center gap-1.5 rounded-xl border border-slate-200 px-3 text-[12px] font-bold text-slate-500 transition-all hover:border-slate-300 hover:text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <RefreshCw size={13} />
                Обновить каталог
              </button>
            </div>

            <ProductsTable
              rows={report.rows}
              selectionTotals={report.selectionTotals}
              page={report.page}
              size={report.size}
              totalRows={report.totalRows}
              totalPages={report.totalPages}
              filtered={hasFilter}
              onPageChange={setPage}
              sort={sort}
              order={order}
              onSort={onSort}
            />

            {report.skusMissingFromCatalog.length > 0 && (
              <div className="mt-2 flex items-start gap-2 text-[11.5px] font-semibold leading-relaxed text-amber-600">
                <AlertTriangle size={13} className="mt-0.5 shrink-0" />
                <span>
                  {report.skusMissingFromCatalog.length} SKU из начислений нет в каталоге — их расходы
                  не попали в товары.{' '}
                  {/* Список приходит готовым: показывать конкретные номера позволяет
                      то, что он уже есть в ответе, — а «обновите каталог» без номера
                      оставляло человека гадать, к какому именно товару это относится. */}
                  SKU:{' '}
                  <span className="tnum font-extrabold">
                    {report.skusMissingFromCatalog.slice(0, 20).join(', ')}
                  </span>
                  {report.skusMissingFromCatalog.length > 20 && (
                    <> и ещё {report.skusMissingFromCatalog.length - 20}</>
                  )}
                  . Обновите каталог.
                </span>
              </div>
            )}
          </motion.section>

          {/* Отдельная карточка, а не часть блока с фильтрами: график читается как
              самостоятельный раздел страницы, и в общую рамку с фильтрами он попадал
              внутрь таблицы. На «Обзоре» так же устроены все блоки. */}
          <ProductSalesDynamics
            accountId={account.id}
            range={range}
            author={author}
            skus={skus}
            runner={runner}
          />
        </div>
      )}
    </>
  )
}
