import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import { X } from 'lucide-react'
import { getSalesDynamics } from '@/api/client'
import { describeError } from '@/api/http'
import SalesDynamicsChart from '@/components/finance/SalesDynamicsChart'
import type { Coverage, DateRange, SalesDay } from '@/types'

/** Какой отбор показан на графике. */
type Mode = 'all' | 'author' | 'sku'

/**
 * График динамики продаж под таблицей товаров.
 *
 * <p>Данные запрашиваются здесь, а не берутся из отчёта по товарам: у него строки
 * это товары, а графику нужен ряд по дням. Считать его на клиенте означало бы
 * разложить товары по днятам у себя, то есть скачать весь отчёт только ради рисунка.
 *
 * <p>Автор и SKU — два взаимно исключающих отбора, поэтому это переключатель, а не два
 * независимых поля. Иначе возник бы вопрос «а что показывать, если выбрано и то и
 * другое», и ответ был бы «непонятно»: заданный SKU означал бы искать товар сразу у
 * двух продавцов.
 */
export default function ProductSalesDynamics({
  mp,
  range,
  authors,
}: {
  mp: string
  range: DateRange
  authors: string[]
}) {
  const [mode, setMode] = useState<Mode>('all')
  const [author, setAuthor] = useState('')
  const [sku, setSku] = useState('')
  const [data, setData] = useState<{ days: SalesDay[]; coverage: Coverage; filter: string } | null>(null)
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

  const load = useCallback(async () => {
    const t = ++token.current
    setLoading(true)
    try {
      const parsedSku = mode === 'sku' ? Number(sku.trim()) : undefined
      const r = await getSalesDynamics(mp, {
        dateFrom: range.from,
        dateTo: range.to,
        author: mode === 'author' && author.trim() ? author.trim() : undefined,
        sku: parsedSku && Number.isFinite(parsedSku) ? parsedSku : undefined,
      })
      if (token.current !== t) return
      setData({ days: r.days, coverage: r.coverage, filter: r.filter })
      setError(null)
    } catch (e) {
      if (token.current !== t) return
      setError(describeError(e))
    } finally {
      if (token.current === t) setLoading(false)
    }
  }, [mp, range.from, range.to, mode, author, sku, filterKey])

  useEffect(() => {
    void load()
  }, [load])

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
      <div className="flex flex-wrap items-center justify-between gap-3">
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
          <SalesDynamicsChart
            points={points}
            filterLabel={activeLabel}
            loading={loading}
          />
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

  const out: { date: string; state: 'ok' | 'missing' | 'failed' | 'provisional'; sold: number; returned: number }[] = []
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
    })
  }
  return out
}
