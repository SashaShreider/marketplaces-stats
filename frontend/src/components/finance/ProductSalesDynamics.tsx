import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { BarChart3, Table2, X } from 'lucide-react'
import { getSalesDynamics } from '@/api/client'
import { describeError } from '@/api/http'
import SalesDynamicsChart, { type Point } from '@/components/finance/SalesDynamicsChart'
import SalesDynamicsTable from '@/components/finance/SalesDynamicsTable'
import type { ImportRunner } from '@/features/imports/useImportRunner'
import type { Coverage, DateRange, SalesDay } from '@/types'
import { cn } from '@/utils/cn'

/** Какой отбор показан на графике. */
type Mode = 'all' | 'author' | 'sku'

/** Второй вид раздела: график и таблица показывают одни и те же дни. */
type View = 'chart' | 'table'

const VIEWS = [
  { id: 'chart', label: 'График', icon: BarChart3 },
  { id: 'table', label: 'Таблица', icon: Table2 },
] as const

/**
 * Динамика продаж под таблицей товаров: график и таблица одних и тех же дней.
 *
 * <p>Данные запрашиваются здесь, а не берутся из отчёта по товарам: у него строки
 * это товары, а разделу нужен ряд по дням. Считать его на клиенте означало бы
 * разложить товары по днятам у себя, то есть скачать весь отчёт только ради рисунка.
 *
 * <p>Деньги в таблице — экономика конкретных книг: в расходы входят только начисления
 * с привязкой к SKU, поэтому выручка сходится с первой таблицей на «Товарах».
 *
 * <p>Автор и SKU — два взаимно исключающих отбора, поэтому это переключатель, а не два
 * независимых поля. Иначе возник бы вопрос «а что показывать, если выбрано и то и
 * другое», и ответ был бы «непонятно»: заданный SKU означал бы искать товар сразу у
 * двух продавцов.
 */
export default function ProductSalesDynamics({
  accountId,
  range,
  authors,
  runner,
}: {
  /** Магазин, чьи данные показываются: у каждого кабинеда график свой */
  accountId: number
  range: DateRange
  authors: string[]
  /** Нужен ради слежения за ходом импорта: догружается период — график должен достраиваться сам */
  runner: ImportRunner
}) {
  const [mode, setMode] = useState<Mode>('all')
  const [view, setView] = useState<View>('chart')
  const [author, setAuthor] = useState('')
  const [sku, setSku] = useState('')
  const [data, setData] = useState<{
    days: SalesDay[]
    coverage: Coverage
    filter: string
    unallocatedExpenses: number
  } | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const token = useRef(0)

  // Меняется только при переключении отбора, а не при каждом нажатии клавиши:
  // иначе запрос уходил бы на каждое слово введённого номера.
  const filterKey = useMemo(() => {
    if (mode === 'author') return `a:${author.trim()}`
    if (mode === 'sku') return `s:${sku.trim()}`
    return 'all'
  }, [mode, author, sku])

  /**
   * Загрузка данных графика.
   *
   * @param silent обновление по ходу импорта. Полоса загрузки при нём не мигает:
   *               данные прибывают постепенно, и мигание на каждом обращении к серверу
   *               выглядело бы как «страница дёргается», хотя она дописывается
   */
  const load = useCallback(async (silent = false) => {
    const t = ++token.current
    if (!silent) setLoading(true)
    try {
      const parsedSku = mode === 'sku' ? Number(sku.trim()) : undefined
      const r = await getSalesDynamics(accountId, {
        dateFrom: range.from,
        dateTo: range.to,
        author: mode === 'author' && author.trim() ? author.trim() : undefined,
        sku: parsedSku && Number.isFinite(parsedSku) ? parsedSku : undefined,
      })
      if (token.current !== t) return
      setData({
        days: r.days,
        coverage: r.coverage,
        filter: r.filter,
        unallocatedExpenses: r.unallocatedExpenses,
      })
      setError(null)
    } catch (e) {
      if (token.current !== t) return
      setError(describeError(e))
    } finally {
      if (token.current === t && !silent) setLoading(false)
    }
  }, [accountId, range.from, range.to, mode, author, sku, filterKey])

  useEffect(() => {
    void load()
  }, [load])

  // Данные подтягиваются по ходу импорта, без перезагрузки страницы. На «Обзоре» так
  // же: страница следит за прогрессом и обновляет отчёт, а пользователь видит, как
  // график достраивается. Без этого период оставался бы недогруженным до ручного
  // обновления страницы.
  const run = runner.run
  const progressKey = run ? `${run.id}:${run.doneUnits}:${run.failedUnits}:${run.inProgress}` : ''
  const lastProgress = useRef(progressKey)
  useEffect(() => {
    if (progressKey === lastProgress.current) return
    lastProgress.current = progressKey
    void load(true)
  }, [progressKey, load])

  const points = useMemo(
    () => (data ? buildPoints(data.days, data.coverage, range) : []),
    [data, range.from, range.to],
  )

  const filters = [
    { id: 'all' as const, label: 'Все товары' },
    { id: 'author' as const, label: 'По автору' },
    { id: 'sku' as const, label: 'По SKU' },
  ]

  const activeLabel =
    mode === 'sku' && sku.trim() ? data?.filter ?? `SKU ${sku.trim()}` : (data?.filter ?? '')

  return (
    <motion.section
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, delay: 0.3 }}
      className="mt-4 rounded-2xl border border-slate-200/70 bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
    >
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="text-[17px] font-extrabold tracking-tight text-slate-900">
          Динамика продаж
        </h2>

        <div className="flex flex-wrap items-center gap-2">
          <div className="flex rounded-full bg-slate-100 p-1">
            {filters.map((f) => (
              <button
                key={f.id}
                onClick={() => setMode(f.id)}
                className={
                  mode === f.id
                    ? 'rounded-full bg-white px-3 py-1.5 text-[12px] font-bold text-brand-700 shadow-sm ring-1 ring-slate-200/70'
                    : 'rounded-full px-3 py-1.5 text-[12px] font-bold text-slate-500 transition-colors hover:text-slate-700'
                }
              >
                {f.label}
              </button>
            ))}
          </div>

          {mode === 'author' && (
            <input
              list="dynamics-authors"
              value={author}
              onChange={(e) => setAuthor(e.target.value)}
              placeholder="Имя автора"
              className="w-[210px] rounded-xl border border-slate-200 bg-slate-50/50 px-3.5 py-2 text-[13px] font-semibold text-slate-800 outline-none transition-all placeholder:font-medium placeholder:text-slate-300 focus:border-brand-400 focus:bg-white focus:ring-4 focus:ring-brand-500/10"
            />
          )}

          {mode === 'sku' && (
            <input
              value={sku}
              onChange={(e) => setSku(e.target.value.replace(/\D/g, ''))}
              placeholder="SKU"
              inputMode="numeric"
              className="tnum w-[150px] rounded-xl border border-slate-200 bg-slate-50/50 px-3.5 py-2 text-[13px] font-semibold text-slate-800 outline-none transition-all placeholder:font-medium placeholder:text-slate-300 focus:border-brand-400 focus:bg-white focus:ring-4 focus:ring-brand-500/10"
            />
          )}

          {(mode === 'author' && author) || (mode === 'sku' && sku) ? (
            <button
              onClick={() => {
                setAuthor('')
                setSku('')
              }}
              title="Сбросить отбор"
              className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
            >
              <X size={14} />
            </button>
          ) : null}
        </div>

        {/* Переключатель «График / Таблица» — тот же, что у «Динамики финансов»
            на «Обзоре»: раздел один, а посмотреть его можно двумя способами. */}
        <div className="ml-auto flex rounded-full bg-slate-100 p-1">
          {VIEWS.map(({ id, label, icon: Icon }) => {
            const active = view === id
            return (
              <button
                key={id}
                onClick={() => setView(id)}
                className="relative flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-[12px] font-bold"
              >
                {active && (
                  <motion.span
                    layoutId="dynamics-view-pill"
                    className="absolute inset-0 rounded-full bg-brand-600 shadow-md shadow-brand-600/25"
                    transition={{ type: 'spring', stiffness: 420, damping: 34 }}
                  />
                )}
                <Icon size={14} className={cn('relative z-10', active ? 'text-white' : 'text-slate-400')} />
                <span className={cn('relative z-10 transition-colors', active ? 'text-white' : 'text-slate-500')}>
                  {label}
                </span>
              </button>
            )
          })}
        </div>
      </div>

      {/* Подсказки те же строки, что лежат в базе, поэтому выбранное значение
          гарантированно что-то найдёт: вводить руками имеет смысл только точным
          написанием из карточки товара. */}
      <datalist id="dynamics-authors">
        {authors.map((a) => (
          <option key={a} value={a} />
        ))}
      </datalist>

      <div className="mt-4">
        {error ? (
          <p className="py-10 text-center text-[13px] font-semibold text-rose-600">{error}</p>
        ) : (
          <AnimatePresence mode="wait" initial={false}>
            {view === 'chart' ? (
              <motion.div
                key="chart"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.22 }}
              >
                <SalesDynamicsChart
                  points={points}
                  filterLabel={activeLabel}
                  loading={loading}
                />
              </motion.div>
            ) : (
              <motion.div
                key="table"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.22 }}
              >
                <SalesDynamicsTable
                  points={points}
                  filterLabel={activeLabel}
                  unallocatedExpenses={data?.unallocatedExpenses ?? 0}
                  loading={loading}
                />
              </motion.div>
            )}
          </AnimatePresence>
        )}
      </div>
    </motion.section>
  )
}

/** Разворачивает дни ответа в ряд по всему периоду — см. SalesDynamicsChart. */
function buildPoints(
  days: SalesDay[],
  coverage: Coverage,
  range: DateRange,
) {
  const missing = new Set(coverage.missingDays)
  const failed = new Set(coverage.failedDates)
  const provisional = new Set(coverage.provisionalDays)
  const byDate = new Map(days.map((d) => [d.date, d]))

  const from = new Date(`${range.from}T00:00:00`)
  const to = new Date(`${range.to}T00:00:00`)
  if (isNaN(from.getTime()) || isNaN(to.getTime())) {
    return days.map((d) => ({ ...d, state: 'ok' as const }))
  }

  const out: Point[] = []
  for (let d = new Date(from); d <= to; d.setDate(d.getDate() + 1)) {
    const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    const found = byDate.get(iso)
    out.push({
      date: iso,
      state: failed.has(iso)
        ? 'failed'
        : missing.has(iso)
          ? 'missing'
          : provisional.has(iso)
            ? 'provisional'
            : 'ok',
      sold: found?.sold ?? 0,
      returned: found?.returned ?? 0,
      income: found?.income ?? 0,
      commission: found?.commission ?? 0,
      logistics: found?.logistics ?? 0,
      promotion: found?.promotion ?? 0,
      otherExpenses: found?.otherExpenses ?? 0,
      revenue: found?.revenue ?? 0,
    })
  }
  return out
}
