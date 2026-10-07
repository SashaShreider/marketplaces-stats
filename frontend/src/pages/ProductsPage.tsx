import { useCallback, useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import {
  AlertTriangle, ChevronLeft, ChevronRight, CloudDownload, Info, Package, RefreshCw, Search, X,
} from 'lucide-react'
import { getAuthors, getProducts } from '@/api/client'
import { describeError } from '@/api/http'
import DateRangePicker from '@/components/common/DateRangePicker'
import EmptyState from '@/components/marketplace/EmptyState'
import ProgressBar from '@/components/sync/ProgressBar'
import SyncIndicator, { syncStateOf } from '@/components/sync/SyncIndicator'
import { AnimatedNumber, MarketplaceLogo, PageHeader } from '@/components/ui'
import type { ImportRunner } from '@/features/imports/useImportRunner'
import type { DateRange, MarketplaceInfo, ProductRow, ProductSort, ProductsReport } from '@/types'
import { cn } from '@/utils/cn'
import { fmtDateTime, fmtMoney, fmtNum } from '@/utils/format'

const PAGE_SIZE = 25

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

function Stat({
  label,
  children,
  sub,
  tone,
  delay,
}: {
  label: string
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
      <div className="text-[11px] font-extrabold uppercase tracking-[0.1em] text-slate-500">{label}</div>
      <div className="mt-2 text-[24px] font-extrabold leading-none tracking-tight text-slate-900">{children}</div>
      {sub && <div className="mt-2 text-[12px] font-medium text-slate-500">{sub}</div>}
    </motion.div>
  )
}

function Thumb({ src, name }: { src: string | null; name: string }) {
  const [failed, setFailed] = useState(false)
  if (!src || failed) {
    return (
      <span className="flex h-12 w-9 shrink-0 items-center justify-center rounded-md bg-slate-100 text-slate-300">
        <Package size={16} />
      </span>
    )
  }
  return (
    <img
      src={src}
      alt={name}
      loading="lazy"
      onError={() => setFailed(true)}
      className="h-12 w-9 shrink-0 rounded-md bg-slate-100 object-cover ring-1 ring-slate-200/70"
    />
  )
}

function Row({ r }: { r: ProductRow }) {
  const sold = r.soldQuantity ?? r.quantity ?? 0
  const returned = r.returnedQuantity ?? 0
  const authors = r.authors.map((a) => a.raw)
  return (
    <tr className="group transition-colors hover:bg-brand-50/50">
      <td className="border-b border-slate-100 px-4 py-3 group-hover:border-brand-100">
        <div className="flex items-center gap-3">
          <Thumb src={r.primaryImage} name={r.name} />
          <div className="min-w-0 max-w-[420px]">
            <div className="line-clamp-2 text-[13px] font-bold leading-snug text-slate-800" title={r.name}>
              {r.name}
            </div>
            <div className="mt-0.5 truncate text-[11.5px] font-medium text-slate-400">
              {authors.length > 0 ? authors.join(', ') : 'Автор не указан'}
              {r.isbn && <span> · ISBN {r.isbn}</span>}
            </div>
          </div>
        </div>
      </td>
      <td className="whitespace-nowrap border-b border-slate-100 px-4 py-3 text-left group-hover:border-brand-100">
        <div className="tnum text-[12.5px] font-bold text-slate-700">{r.sku}</div>
        {r.offerId && <div className="text-[11px] font-medium text-slate-400">{r.offerId}</div>}
      </td>
      <td className="whitespace-nowrap border-b border-slate-100 px-4 py-3 text-right group-hover:border-brand-100">
        <span className={cn('tnum text-[13px] font-bold', sold > 0 ? 'text-slate-800' : 'text-slate-300')}>
          {fmtNum(sold)}
        </span>
        {returned > 0 && (
          <span className="tnum ml-1.5 text-[11px] font-semibold text-rose-500">−{fmtNum(returned)}</span>
        )}
      </td>
      <td className="tnum whitespace-nowrap border-b border-slate-100 px-4 py-3 text-right text-[13px] font-extrabold text-slate-900 group-hover:border-brand-100">
        {r.income ? fmtMoney(r.income) : <span className="text-slate-300">0 ₽</span>}
      </td>
      <td className="tnum whitespace-nowrap border-b border-slate-100 px-4 py-3 text-right text-[13px] font-semibold text-rose-500 group-hover:border-brand-100">
        {r.expenses ? `−${fmtMoney(Math.abs(r.expenses))}` : <span className="text-slate-300">0 ₽</span>}
      </td>
    </tr>
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
          state={syncStateOf(report, run)}
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
  const hasFilter = Boolean(dq || da)

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
              <Stat label="Доходы" tone="border-emerald-200/60 bg-emerald-50/60" delay={0.05}>
                <AnimatedNumber value={totals.income} />
              </Stat>
              <Stat
                label="Расходы по товарам"
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
                label="Продано"
                tone="border-brand-200/60 bg-brand-50/60"
                delay={0.15}
                sub={
                  (totals.returnedQuantity ?? 0) > 0 ? `возвращено ${fmtNum(totals.returnedQuantity ?? 0)} шт` : undefined
                }
              >
                <AnimatedNumber value={totals.soldQuantity ?? 0} suffix=" шт" />
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

            {/* Таблица */}
            <div className="mt-4 overflow-hidden rounded-xl border border-slate-200/80">
              <div className="scroll-slim max-h-[620px] overflow-auto">
                <table className="w-full min-w-[820px] border-separate border-spacing-0">
                  <thead>
                    <tr>
                      {[
                        ['Товар', 'text-left'],
                        ['SKU / артикул', 'text-left'],
                        ['Продано', 'text-right'],
                        ['Доход', 'text-right'],
                        ['Расходы', 'text-right'],
                      ].map(([l, a]) => (
                        <th
                          key={l}
                          className={cn(
                            'sticky top-0 z-10 whitespace-nowrap border-b border-slate-200 bg-slate-50/95 px-4 py-3 text-[10.5px] font-extrabold uppercase tracking-[0.08em] text-slate-400 backdrop-blur',
                            a,
                          )}
                        >
                          {l}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {report.rows.map((r) => (
                      <Row key={r.sku} r={r} />
                    ))}
                    {report.rows.length === 0 && (
                      <tr>
                        <td colSpan={5} className="px-4 py-14 text-center text-[13px] font-medium text-slate-400">
                          {hasFilter ? 'По вашему запросу ничего не найдено' : 'Товаров нет'}
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>

              <div className="flex items-center justify-between gap-3 border-t border-slate-100 px-4 py-2">
                <span className="text-[12px] font-medium text-slate-400">
                  Найдено: <span className="tnum font-bold text-slate-600">{fmtNum(report.totalRows)}</span>
                </span>
                <div className="flex items-center gap-3">
                  <span className="text-[12px] font-semibold text-slate-500">
                    Страница <span className="tnum font-extrabold text-slate-800">{report.page + 1}</span> из{' '}
                    <span className="tnum font-extrabold text-slate-800">{Math.max(1, report.totalPages)}</span>
                  </span>
                  <div className="flex gap-1">
                    <button
                      disabled={page === 0}
                      onClick={() => setPage(page - 1)}
                      className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-all hover:border-brand-300 hover:text-brand-600 disabled:cursor-not-allowed disabled:opacity-35"
                    >
                      <ChevronLeft size={15} />
                    </button>
                    <button
                      disabled={page >= report.totalPages - 1}
                      onClick={() => setPage(page + 1)}
                      className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-all hover:border-brand-300 hover:text-brand-600 disabled:cursor-not-allowed disabled:opacity-35"
                    >
                      <ChevronRight size={15} />
                    </button>
                  </div>
                </div>
              </div>
            </div>

            {report.unallocatedExpenses > 0 && (
              <div className="mt-3 flex items-start gap-2 text-[11.5px] font-medium leading-relaxed text-slate-400">
                <Info size={13} className="mt-0.5 shrink-0" />
                Расходы по товарам меньше, чем в «Обзоре»: часть списаний (контейнеры, прочие услуги)
                маркетплейс присылает без привязки к SKU — {fmtMoney(report.unallocatedExpenses)} за период.
              </div>
            )}
            {report.skusMissingFromCatalog.length > 0 && (
              <div className="mt-2 flex items-start gap-2 text-[11.5px] font-semibold leading-relaxed text-amber-600">
                <AlertTriangle size={13} className="mt-0.5 shrink-0" />
                {report.skusMissingFromCatalog.length} SKU из начислений нет в каталоге — их расходы не
                попали в товары. Обновите каталог.
              </div>
            )}
          </motion.section>
        </div>
      )}
    </>
  )
}
