import { useCallback, useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import { AlertTriangle, CloudDownload, Info, Package, RefreshCw, Search, X } from 'lucide-react'
import { getAuthors, getProducts } from '@/api/client'
import { describeError } from '@/api/http'
import DateRangePicker from '@/components/common/DateRangePicker'
import ProductsTable from '@/components/finance/ProductsTable'
import EmptyState from '@/components/marketplace/EmptyState'
import ProductSalesDynamics from '@/components/finance/ProductSalesDynamics'
import ProgressBar from '@/components/sync/ProgressBar'
import SyncIndicator from '@/components/sync/SyncIndicator'
import { AnimatedNumber, MarketplaceLogo, PageHeader } from '@/components/ui'
import { useAutoSync } from '@/features/imports/useAutoSync'
import type { ImportRunner } from '@/features/imports/useImportRunner'
import type { DateRange, MarketplaceInfo, ProductSort, ProductsReport } from '@/types'
import { cn } from '@/utils/cn'
import { fmtDateTime, fmtMoney, fmtNum, revenue } from '@/utils/format'

const PAGE_SIZE = 200

const SORTS: { id: ProductSort; label: string }[] = [
  { id: 'INCOME', label: 'По доходу' },
  { id: 'NAME', label: 'По названию' },
  { id: 'SKU', label: 'По SKU' },
]

function useDebounced<T>(value: T, ms = 350): T {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = window.setTimeout(() => setV(value), ms)
    return () => window.clearTimeout(t)
  }, [value, ms])
  return v
}

/**
 * Значок с подсказкой при наведении.
 *
 * <p>Подсказка на CSS, а не на состоянии: наведение мыши не должно вызывать
 * перерисовку страницы, а появление и скрытие подсказки не должно требовать
 * обработчиков, которые нужно не забыть снять.
 */
function Hint({ text }: { text: string }) {
  return (
    <span className="group/hint relative inline-flex align-middle">
      <Info
        size={13}
        className="cursor-help text-slate-400 transition-colors group-hover/hint:text-slate-600"
      />
      <span
        role="tooltip"
        className="pointer-events-none absolute bottom-full left-1/2 z-20 mb-2 w-64 -translate-x-1/2 rounded-lg bg-slate-900 px-3 py-2 text-[12px] font-medium normal-case leading-relaxed tracking-normal text-white opacity-0 shadow-lg transition-opacity duration-150 group-hover/hint:opacity-100"
      >
        {text}
      </span>
    </span>
  )
}

function Stat({
  label,
  hint,
  children,
  sub,
  tone,
  delay,
}: {
  label: string
  /** Объяснение подписи, если её нельзя прочитать буквально */
  hint?: string
  children: React.ReactNode
  sub?: React.ReactNode
  tone: string
  delay: number
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay }}
      className={cn('rounded-2xl border p-4', tone)}
    >
      <div className="flex items-center gap-1.5 text-[11px] font-extrabold uppercase tracking-[0.1em] text-slate-500">
        {label}
        {hint && <Hint text={hint} />}
      </div>
      <div className="mt-2 text-[24px] font-extrabold leading-none tracking-tight text-slate-900">{children}</div>
      {sub && <div className="mt-2 text-[12px] font-medium text-slate-500">{sub}</div>}
    </motion.div>
  )
}


export default function ProductsPage({
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
  const [query, setQuery] = useState('')
  const [author, setAuthor] = useState('')
  const [sort, setSort] = useState<ProductSort>('INCOME')
  const [page, setPage] = useState(0)
  const [authors, setAuthors] = useState<string[]>([])

  const [report, setReport] = useState<ProductsReport | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const token = useRef(0)

  const dq = useDebounced(query.trim())
  const da = useDebounced(author.trim())

  // подсказки для фильтра по автору
  useEffect(() => {
    if (!mp.connected) return
    getAuthors(mp.code).then(setAuthors).catch(() => setAuthors([]))
  }, [mp.code, mp.connected, mp.catalogProducts])

  // фильтры меняются — возвращаемся на первую страницу
  useEffect(() => setPage(0), [dq, da, sort, range.from, range.to])

  const reload = useCallback(
    async (silent = false) => {
      const t = ++token.current
      if (!silent) setLoading(true)
      try {
        const r = await getProducts(mp.code, {
          dateFrom: range.from,
          dateTo: range.to,
          query: dq || undefined,
          author: da || undefined,
          sort,
          page,
          size: PAGE_SIZE,
        })
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
    [mp.code, range.from, range.to, dq, da, sort, page],
  )

  useEffect(() => {
    if (mp.connected) reload()
  }, [mp.connected, reload])

  // обновляем по ходу импортов
  const run = runner.run
  const progressKey = run ? `${run.id}:${run.doneUnits}:${run.failedUnits}:${run.inProgress}` : ''
  const lastKey = useRef(progressKey)
  useEffect(() => {
    if (progressKey === lastKey.current) return
    lastKey.current = progressKey
    reload(true)
  }, [progressKey, reload])

  // Догружаем недостающие дни выбранного периода, как это делает обзор.
  // Без этого страница товаров молча показывала неполные цифры: период в календаре
  // менялся, дни в фоне не грузились, и отчёт выглядел бы готовым при неполных
  // данных. Отчёт по товарам несёт то же покрытие периода, поэтому хуку достаточно.
  const auto = useAutoSync({ mp: mp.connected ? mp.code : null, report, range, runner })

  const header = (
    <PageHeader
      title="Товары"
      subtitle={
        <>
          Продажи и списания по каждому товару на {mp.name}
          <MarketplaceLogo code={mp.code} size="sm" />
        </>
      }
    >
      <div className="flex items-center gap-1.5">
        <SyncIndicator
          state={auto.state}
          busy={runner.busy}
          onRetry={() => runner.startFinance(range.from, range.to)}
        />
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

  const catalogMissing = report ? !report.catalog.loaded : false
  const totals = report?.totals
  // Пока итогов нет, число всё равно куда-то попадёт в разметку — держим ноль,
  // чтобы карточка не показывала «NaN» между загрузкой и данными.
  const gross = revenue(totals?.income ?? 0, totals?.expenses ?? 0)
  const hasFilter = Boolean(dq || da)
  // Подсказка объясняет, почему расходы по товарам меньше, чем в «Обзоре».
  // Раньше это был абзац под таблицей: он висел на странице постоянно и описывал
  // ситуацию, заметную только при сравнении двух экранов. По наведению он
  // появляется ровно тогда, когда человек смотрит на само число.
  const unallocated = report?.unallocatedExpenses ?? 0
  const unallocatedHint =
    unallocated > 0
      ? `Расходы по товарам меньше, чем в «Обзоре»: часть списаний (контейнеры, прочие услуги) маркетплейс присылает без привязки к SKU. Сейчас таких расходов ${fmtMoney(unallocated)}.`
      : 'Часть списаний (контейнеры, прочие услуги) маркетплейс присылает без привязки к SKU — они не попадают в расходы по товарам. Сейчас таких расходов нет.'

  return (
    <>
      {header}
      <ProgressBar runner={runner} />

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
              <Stat
                label="Доходы"
                tone="border-emerald-200/60 bg-emerald-50/60"
                delay={0.05}
                sub={
                  <span className="flex flex-wrap items-center gap-x-2.5 gap-y-0.5">
                    <span>продано {fmtNum(totals.soldQuantity ?? 0)} шт</span>
                    {(totals.returnedQuantity ?? 0) > 0 && (
                      <span>возвращено {fmtNum(totals.returnedQuantity ?? 0)} шт</span>
                    )}
                  </span>
                }
              >
                <AnimatedNumber value={totals.income} />
              </Stat>
              <Stat
                label="Расходы по товарам"
                hint={unallocatedHint}
                tone="border-rose-200/60 bg-rose-50/60"
                delay={0.1}
                sub={
                  report.unallocatedExpenses > 0
                    ? `ещё ${fmtMoney(report.unallocatedExpenses)} без привязки к товару`
                    : undefined
                }
              >
                <AnimatedNumber value={totals.expenses} />
              </Stat>
              <Stat
                label="Выручка"
                tone={gross >= 0 ? 'border-brand-200/60 bg-brand-50/60' : 'border-rose-200/60 bg-rose-50/60'}
                delay={0.1}
              >
                <AnimatedNumber value={gross} />
              </Stat>
              <Stat
                label="Товаров с продажами"
                tone="border-slate-200/80 bg-white"
                delay={0.2}
                sub={`из ${fmtNum(totals.productsInCatalog)} в каталоге`}
              >
                <AnimatedNumber value={totals.productsWithSales} suffix="" />
              </Stat>
            </div>
          )}

          <motion.section
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.45, delay: 0.25 }}
            className="mt-4 rounded-2xl border border-slate-200/70 bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
          >
            {/* Панель фильтров */}
            <div className="flex flex-wrap items-center gap-3">
              <div className="relative min-w-[220px] flex-1">
                <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-300" />
                <input
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Название, артикул или ISBN"
                  className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2.5 pl-9 pr-9 text-[13px] font-semibold text-slate-800 outline-none transition-all placeholder:font-medium placeholder:text-slate-300 focus:border-brand-400 focus:bg-white focus:ring-4 focus:ring-brand-500/10"
                />
                {query && (
                  <button
                    onClick={() => setQuery('')}
                    className="absolute right-2.5 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100"
                  >
                    <X size={13} />
                  </button>
                )}
              </div>

              <div className="relative w-[210px]">
                <input
                  list="authors-list"
                  value={author}
                  onChange={(e) => setAuthor(e.target.value)}
                  placeholder="Автор"
                  // Поиск строгий: ищется ровно то, что введено. Подсказки справа
                  // подставляют значение, которое гарантированно что-то найдёт, —
                  // вводить руками имеет смысл только точным написанием из карточки.
                  title="Ищется точное совпадение с тем, как автор указан в карточке товара. Начните вводить, чтобы выбрать из списка."
                  className="w-full rounded-xl border border-slate-200 bg-slate-50/50 px-3.5 py-2.5 pr-9 text-[13px] font-semibold text-slate-800 outline-none transition-all placeholder:font-medium placeholder:text-slate-300 focus:border-brand-400 focus:bg-white focus:ring-4 focus:ring-brand-500/10"
                />
                <datalist id="authors-list">
                  {authors.map((a) => (
                    <option key={a} value={a} />
                  ))}
                </datalist>
                {author && (
                  <button
                    onClick={() => setAuthor('')}
                    className="absolute right-2.5 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100"
                  >
                    <X size={13} />
                  </button>
                )}
              </div>

              <div className="flex rounded-full bg-slate-100 p-1">
                {SORTS.map((s) => (
                  <button
                    key={s.id}
                    onClick={() => setSort(s.id)}
                    className="relative rounded-full px-3.5 py-1.5 text-[12px] font-bold"
                  >
                    {sort === s.id && (
                      <motion.span
                        layoutId="sort-pill"
                        className="absolute inset-0 rounded-full bg-white shadow-sm ring-1 ring-slate-200/70"
                        transition={{ type: 'spring', stiffness: 420, damping: 34 }}
                      />
                    )}
                    <span className={cn('relative z-10', sort === s.id ? 'text-brand-700' : 'text-slate-500')}>
                      {s.label}
                    </span>
                  </button>
                ))}
              </div>

              <button
                disabled={runner.busy}
                onClick={() => runner.startCatalog()}
                title="Каталог переписывается целиком (1 запрос к маркетплейсу)"
                className="flex items-center gap-1.5 rounded-xl border border-slate-200 px-3 py-2.5 text-[12px] font-bold text-slate-500 transition-all hover:border-slate-300 hover:text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <RefreshCw size={13} />
                Обновить каталог
              </button>
            </div>

            <div className="mt-2.5 text-[11.5px] font-medium text-slate-400">
              В каталоге {fmtNum(report.catalog.products)} товаров
              {report.catalog.lastSyncedAt && <> · обновлён {fmtDateTime(report.catalog.lastSyncedAt)}</>}
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
          <ProductSalesDynamics mp={mp.code} range={range} authors={authors} runner={runner} />
        </div>
      )}
    </>
  )
}
