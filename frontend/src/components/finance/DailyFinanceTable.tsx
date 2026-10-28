import { useMemo } from 'react'
import { Download } from 'lucide-react'
import type { DayFinance, DayState, ExpenseCategory } from '@/types'
import DataTable, { type DataColumn } from '@/components/ui/DataTable'
import { SecondaryButton } from '@/components/ui/controls'
import { EXPENSE_ORDER, orderedGroups } from '@/components/finance/SummaryCards'
import { cn } from '@/utils/cn'
import { downloadCsv, safeFilename, toCsv } from '@/utils/csv'
import { fmtDateNumeric, fmtMoney2, fmtNum } from '@/utils/format'

/**
 * Пометки состояния дня.
 *
 * <p>Раньше состояние было отдельной колонкой, и она была пуста почти всегда: у
 * нормального дня там нечего писать. Теперь это точка рядом с датой, а расшифровка —
 * в подсказке. Колонка «состояние» перестала занимать место, но сам факт «день не
 * загружен» по-прежнему виден, и его нельзя потерять: без него нули в строке
 * читаются как «денег не было».
 */
const STATE_LABEL: Record<DayState, string | null> = {
  ok: null,
  provisional: 'Данные ещё могут уточниться',
  missing: 'Данные за этот день не загружены',
  failed: 'Не удалось загрузить данные за этот день',
}

const STATE_DOT: Record<DayState, string> = {
  ok: '',
  provisional: 'bg-sky-400',
  missing: 'bg-amber-400',
  failed: 'bg-rose-500',
}

/** Пометка состояния для CSV: суффикс к дате, чтобы отметка не потерялась в файле. */
const STATE_SUFFIX: Record<DayState, string> = {
  ok: '',
  provisional: ' (уточняется)',
  missing: ' (не загружен)',
  failed: ' (ошибка)',
}

/**
 * Округляет сумму до копеек.
 *
 * <p>Итоги складываются из чисел с двумя знаками, но в двоичной плавающей точке это
 * не точно: 1260.43 + 882.57 даёт `2143.0000000000005`, а сумма по статье —
 * `1260.4299999999998`. В интерфейсе это скрыто форматированием, а в CSV нет: там
 * сырое число, и Excel покажет его целиком — вместо суммы человек увидит мусор.
 * Копейка и есть предел точности таких сумм, поэтому округляем перед выгрузкой.
 */
function round2(n: number): number {
  return Math.round(n * 100) / 100
}

/** Значение статьи расходов дня; пустая статья — ноль, а не пропуск. */
function categoryAmount(day: DayFinance, category: ExpenseCategory): number {
  const group = day.expenseBreakdown?.groups.find((g) => g.category === category)
  return Math.abs(group?.amount ?? 0)
}

function sumCategory(days: DayFinance[], category: ExpenseCategory): number {
  return days.reduce((sum, d) => sum + categoryAmount(d, category), 0)
}

/**
 * Аналитика по дням: таблица, которая раньше была вторым режимом графика.
 *
 * <p>Колонки сгруппированы по смыслу — доходы, расходы, итог, количество, — поэтому
 * в шапке два уровня: группа и колонки внутри неё. Одиннадцать колонок подряд без
 * групп читаются как список чисел, а не как отчёт.
 *
 * <p>Суммы — с копейками: сумма колонок обязана совпадать с итогом, а округлённые до
 * рубля слагаемые этого не делают.
 */
export default function DailyFinanceTable({ days }: { days: DayFinance[] }) {
  const columns = useMemo<DataColumn<DayFinance>[]>(() => {
    const money = (
      key: string,
      label: string,
      group: string,
      pick: (d: DayFinance) => number,
      opts: { negative?: boolean; strong?: boolean; csvLabel?: string } = {},
    ): DataColumn<DayFinance> => ({
      key,
      label,
      group,
      // В выгрузке шапка плоская, и группа пропадает. Поэтому у колонок, чьё имя
      // совпадает с группой («Доходы» в группе «Доходы»), в CSV стоит уточнение —
      // иначе в файле окажутся две одинаковые шапки, и неясно, какая из них итог.
      csvLabel: opts.csvLabel,
      value: (d) => {
        const v = pick(d)
        return (
          <span
            className={cn(
              'tnum text-[12px]',
              opts.strong ? 'font-extrabold text-slate-900' : 'font-semibold text-slate-700',
            )}
          >
            {opts.negative && v !== 0 && '−'}
            {fmtMoney2(Math.abs(v))}
          </span>
        )
      },
      // В выгрузку — сырое число со знаком: в Excel его можно сложить, а
      // отформатированную строку «−12 879,33 ₽» — нельзя.
      csv: (d) => {
        const v = pick(d)
        return opts.negative ? -Math.abs(v) : v
      },
    })

    const expenseColumns = EXPENSE_ORDER.map((category) => {
      const label = orderedGroups(undefined).find((g) => g.category === category)!.label
      return money(category.toLowerCase(), label, 'Расходы', (d) => categoryAmount(d, category), {
        negative: true,
      })
    })

    return [
      {
        key: 'date',
        label: 'Дата',
        align: 'left',
        value: (d) => (
          <span className="inline-flex items-center gap-1.5">
            {d.state !== 'ok' && (
              <span
                className={cn('h-1.5 w-1.5 shrink-0 rounded-full', STATE_DOT[d.state])}
                title={STATE_LABEL[d.state] ?? undefined}
              />
            )}
            <span
              className={cn(
                'tnum text-[12px] font-bold',
                d.state === 'missing' || d.state === 'failed' ? 'text-slate-400' : 'text-slate-800',
              )}
              title={STATE_LABEL[d.state] ?? undefined}
            >
              {fmtDateNumeric(d.date)}
            </span>
          </span>
        ),
        csv: (d) => fmtDateNumeric(d.date) + STATE_SUFFIX[d.state],
        title: (d) => STATE_LABEL[d.state] ?? undefined,
      },
      money('sales', 'Продажи', 'Доходы', (d) => d.sales),
      money('returns', 'Возвраты', 'Доходы', (d) => d.returns, { negative: true }),
      money('partners', 'Бонусы', 'Доходы', (d) => d.partners),
      money('income', 'Доходы', 'Доходы', (d) => d.income, {
        strong: true,
        csvLabel: 'Доходы, всего',
      }),
      ...expenseColumns,
      money('expenses', 'Расходы', 'Расходы', (d) => d.expenses, {
        negative: true,
        strong: true,
        csvLabel: 'Расходы, всего',
      }),
      {
        key: 'profit',
        label: 'Прибыль',
        group: 'Итог',
        value: (d) => (
          <span
            className={cn(
              'tnum text-[12px] font-extrabold',
              d.profit >= 0 ? 'text-emerald-600' : 'text-rose-600',
            )}
          >
            {d.profit < 0 && '−'}
            {fmtMoney2(Math.abs(d.profit))}
          </span>
        ),
        csv: (d) => d.profit,
      },
      {
        key: 'payout',
        label: 'К выплате',
        group: 'Итог',
        value: (d) =>
          d.payout === null ? (
            <span
              className="text-[12px] font-semibold text-slate-300"
              title="Выплата известна только по всему магазину — для отбора её долю не выделить"
            >
              —
            </span>
          ) : (
            <span className="tnum text-[12px] font-semibold text-slate-700">
              {fmtMoney2(d.payout)}
            </span>
          ),
        csv: (d) => d.payout,
      },
      {
        key: 'sold',
        label: 'Продано, шт',
        group: 'Количество',
        value: (d) => (
          <span className="tnum text-[12px] font-semibold text-slate-700">{fmtNum(d.soldQty)}</span>
        ),
        csv: (d) => d.soldQty,
      },
      {
        key: 'returned',
        label: 'Возвращено, шт',
        group: 'Количество',
        value: (d) => (
          <span className="tnum text-[12px] font-semibold text-slate-700">
            {fmtNum(d.returnedQty)}
          </span>
        ),
        csv: (d) => d.returnedQty,
      },
    ]
  }, [])

  const totals = useMemo(() => {
    let payout: number | null = 0
    const sum = {
      sales: 0, returns: 0, partners: 0, income: 0,
      expenses: 0, profit: 0, sold: 0, returned: 0,
    }
    for (const d of days) {
      sum.sales += d.sales
      sum.returns += d.returns
      sum.partners += d.partners
      sum.income += d.income
      sum.expenses += d.expenses
      sum.profit += d.profit
      sum.sold += d.soldQty
      sum.returned += d.returnedQty
      payout = payout === null || d.payout === null ? null : payout + d.payout
    }
    return { ...sum, payout }
  }, [days])

  const totalOf = (key: string): number => {
    switch (key) {
      case 'sales': return totals.sales
      case 'returns': return totals.returns
      case 'partners': return totals.partners
      case 'income': return totals.income
      case 'expenses': return totals.expenses
      case 'profit': return totals.profit
      case 'sold': return totals.sold
      case 'returned': return totals.returned
      default: {
        const category = EXPENSE_ORDER.find((c) => c.toLowerCase() === key)
        return category ? sumCategory(days, category) : 0
      }
    }
  }

  /**
   * Колонки, которые в выгрузке идут со знаком минус.
   *
   * <p>Знак должен совпадать со строками дней: если в теле `−12879.33`, а в итоге
   * `12879.33`, сумма колонки в Excel не сойдётся, и ошибка будет выглядеть как
   * арифметическая, а не как разнобой в знаке.
   */
  const isNegative = (key: string): boolean =>
    key === 'returns' || key === 'expenses' || EXPENSE_ORDER.some((c) => c.toLowerCase() === key)

  const exportCsv = () => {
    const header = columns.map((c) => c.csvLabel ?? c.label)
    const body = days.map((d) =>
      columns.map((c) => {
        const value = c.csv?.(d)
        // Округляем уже в выгрузке, а не при сборке колонок: в интерфейсе суммы
        // идут через `fmtMoney2`, а в CSV — сырые, и без округления там остался бы
        // хвост плавающей точки. Количество — целое, ему округление не вредит.
        if (typeof value === 'number') return round2(value)
        if (value !== undefined) return value
        const rendered = c.value(d)
        return typeof rendered === 'string' || typeof rendered === 'number' ? rendered : ''
      }),
    )
    const totalRow = columns.map((c, i) => {
      if (i === 0) return 'Итого'
      if (c.key === 'payout') return totals.payout === null ? null : round2(totals.payout)
      const value = round2(totalOf(c.key))
      return isNegative(c.key) ? -Math.abs(value) : value
    })
    downloadCsv(
      safeFilename(['Обзор', 'аналитика-по-дням']),
      toCsv([header, ...body, totalRow]),
    )
  }

  return (
    <div className="flex min-h-0 flex-col gap-2">
      <div className="flex items-center justify-between gap-3">
        <span className="text-[12px] font-medium text-slate-400">
          Дней в отчёте: <span className="tnum font-bold text-slate-600">{days.length}</span>
        </span>
        <SecondaryButton onClick={exportCsv} disabled={days.length === 0} title="Выгрузить таблицу в CSV">
          <Download size={13} />
          Экспорт в CSV
        </SecondaryButton>
      </div>

      <DataTable
        rows={days}
        columns={columns}
        rowKey={(d) => d.date}
        // Своя прокрутка, а не прокрутка тела модалки: иначе шапка уезжает
        // вместе с содержимым, и в списке на сотню дней не видно, что за колонка.
        maxHeight="calc(100vh - 15rem)"
        emptyText="За выбранный период дней нет"
        footer={{
          label: 'Итого',
          value: (c) => {
            if (c.key === 'payout') {
              return totals.payout === null ? (
                <span
                  className="text-[12px] font-extrabold text-slate-300"
                  title="Выплата известна только по всему магазину"
                >
                  —
                </span>
              ) : (
                <span className="tnum text-[12.5px] font-extrabold text-slate-900">
                  {fmtMoney2(totals.payout)}
                </span>
              )
            }
            const key = c.key
            if (key === 'sold' || key === 'returned') {
              return (
                <span className="tnum text-[12.5px] font-extrabold text-slate-900">
                  {fmtNum(totalOf(key))}
                </span>
              )
            }
            const value = totalOf(key)
            return (
              <span
                className={cn(
                  'tnum text-[12.5px] font-extrabold',
                  key === 'profit'
                    ? value >= 0
                      ? 'text-emerald-600'
                      : 'text-rose-600'
                    : 'text-slate-900',
                )}
              >
                {isNegative(key) && value !== 0 && '−'}
                {fmtMoney2(Math.abs(value))}
              </span>
            )
          },
        }}
      />
    </div>
  )
}
