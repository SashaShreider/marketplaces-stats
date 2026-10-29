import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import { getSalesDynamics } from '@/api/client'
import { describeError } from '@/api/http'
import SalesDynamicsChart from '@/components/finance/SalesDynamicsChart'
import type { ImportRunner } from '@/features/imports/useImportRunner'
import type { Coverage, DateRange, SalesDay } from '@/types'

/**
 * График динамики продаж под таблицей товаров.
 *
 * <p>Данные запрашиваются здесь, а не берутся из отчёта по товарам: у него строки
 * это товары, а графику нужен ряд по дням. Считать его на клиенте означало бы
 * разложить товары по днятам у себя, то есть скачать весь отчёт только ради рисунка.
 *
 * <p>Отбор графика — тот же, что у таблицы: автор или выбранные товары. Раньше здесь
 * был отдельный переключатель «все / по автору / по SKU», и получалось, что график и
 * таблица над ним отвечают про разные вещи. Один отбор на страницу — один ответ.
 */
export default function ProductSalesDynamics({
  accountId,
  range,
  author,
  skus,
  runner,
}: {
  /** Магазин, чьи данные показываются: у каждого кабинета график свой */
  accountId: number
  range: DateRange
  /** Автор из общего отбора страницы */
  author: string
  /** Выбранные товары; сильнее автора, как и в отчёте */
  skus: number[]
  /** Нужен ради слежения за ходом импорта: догружается период — график должен достраиваться сам */
  runner: ImportRunner
}) {
  const [data, setData] = useState<{ days: SalesDay[]; coverage: Coverage; filter: string } | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const token = useRef(0)

  // Ключ отбора как строка: массив в зависимостях сравнивался бы по ссылке и
  // перезапускал загрузку на каждый рендер.
  const skuKey = useMemo(() => skus.join(','), [skus])

  /**
   * Загрузка данных графика.
   *
   * @param silent обновление по ходу импорта. Полоса загрузки при нём не мигает:
   *               данные прибывают постепенно, и мигание на каждом обращении к серверу
   *               выглядело бы как «страница дёргается», хотя она дописывается
   */
  const load = useCallback(
    async (silent = false) => {
      const t = ++token.current
      if (!silent) setLoading(true)
      try {
        const r = await getSalesDynamics(accountId, {
          dateFrom: range.from,
          dateTo: range.to,
          author: skuKey ? undefined : author.trim() || undefined,
          skus: skuKey ? skus : undefined,
        })
        if (token.current !== t) return
        setData({ days: r.days, coverage: r.coverage, filter: r.filter })
        setError(null)
      } catch (e) {
        if (token.current !== t) return
        setError(describeError(e))
      } finally {
        if (token.current === t && !silent) setLoading(false)
      }
    },
    [accountId, range.from, range.to, author, skuKey, skus],
  )

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

  return (
    <motion.section
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, delay: 0.3 }}
      className="mt-4 rounded-2xl border border-slate-200/70 bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
    >
      <h2 className="text-[17px] font-extrabold tracking-tight text-slate-900">
        Динамика продаж
      </h2>

      <div className="mt-4">
        {error ? (
          <p className="py-10 text-center text-[13px] font-semibold text-rose-600">{error}</p>
        ) : (
          <SalesDynamicsChart
            points={points}
            filterLabel={data?.filter ?? ''}
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
