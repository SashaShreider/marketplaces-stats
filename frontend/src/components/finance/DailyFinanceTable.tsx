import { useMemo } from 'react'
import type { DayFinance, DayState, ExpenseCategory } from '@/types'
import DataTable, { type DataColumn } from '@/components/ui/DataTable'
import { EXPENSE_ORDER, orderedGroups } from '@/components/finance/SummaryCards'
import { cn } from '@/utils/cn'
import { downloadCsv, safeFilename, toCsv } from '@/utils/csv'
import { fmtDateNumeric, fmtMoneyBare, fmtNum } from '@/utils/format'

/** Сколько строк на странице: месяц-полтора, чтобы прокрутка не уставала. */
export const PAGE_SIZE = 90

/**
 * Пометки состояния дня.
 *
 * <p>Раньше состояние было отдельной колонкой, и она была пуста почти всегда: у
 * нормального дня там нечего писать. Теперь это точка рядом с датой, а расшифровка —
 * в подсказке. Сам факт «день не загружен» терять нельзя: без него нули в строке
 * читаются как «денег не было».
 */
const STATE_LABEL: Record<DayState, string | null> = {
  ok: null,
  provisional: 'Данные ещё могут уточниться',
  missing: 'Данные за этот день не загружены',
  failed: 'Не удалось загрузить данные за этот день',
}

/**
 * Пометка состояния для CSV: суффикс к дате, чтобы отметка не потерялась в файле.
 *
 * <p>Кружка рядом с датой больше нет: он висел у каждого ещё не закрытого дня и
 * читался как часть данных, хотя это лишь признак качества. В выгрузке отметка
 * остаётся — там нет ни цвета, ни подсказки, и без суффикса день с ошибкой было
 * бы не отличить от дня без начислений.
 */
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
 * не точно: 1260.43 + 882.57 даёт `2143.0000000000005`. В интерфейсе это скрыто
 * форматированием, а в CSV нет: там сырое число, и Excel покажет его целиком.
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

/** Колонки, которые в выгрузке идут со знаком минус. */
function isNegative(key: string): boolean {
  return key === 'returns' || key === 'expenses' || EXPENSE_ORDER.some((c) => c.toLowerCase() === key)
}

/** Итоги по всем дням периода — их показывает строка «Итого». */
function totalsOf(days: DayFinance[]) {
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
  }
  return sum
}

/**
 * Описание колонок дневной таблицы.
 *
 * <p>Вынесено из компонента: по нему же строится CSV, а выгрузку запускает кнопка
 * в подвале окна. Держать его внутри компонента значило бы дублировать список
 * колонок ради кнопки, которая живёт снаружи.
 */
export function buildColumns(days: DayFinance[]): DataColumn<DayFinance>[] {
  const money = (
    key: string,
    label: string,
    group: string,
    pick: (d: DayFinance) => number,
    opts: { negative?: boolean; strong?: boolean; csvLabel?: string } = {},
  ): DataColumn<DayFinance> => ({
    key,
    // Знак валюты стоит в шапке колонки, а не в каждой ячейке: повторённый
    // двадцать раз «₽» съедает ширину, которую можно отдать цифрам.
    label: `${label}, ₽`,
    group,
    // В выгрузке шапка плоская, и группа пропадает. Поэтому у колонок, чьё имя
    // совпадает с группой («Продажи» в группе «Продажи»), в CSV стоит уточнение —
    // иначе в файле окажутся две одинаковые шапки, и неясно, какая из них итог.
    csvLabel: opts.csvLabel,
    value: (d) => {
      const v = pick(d)
      return (
        <span
          className={cn(
            'tnum text-[11px]',
            opts.strong ? 'font-extrabold text-slate-900' : 'font-semibold text-slate-700',
          )}
        >
          {opts.negative && v !== 0 && '−'}
          {fmtMoneyBare(Math.abs(v))}
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

  void days

  return [
    {
      key: 'date',
      label: 'Дата',
      align: 'left',
      value: (d) => (
        <span
          className={cn(
            'tnum text-[11px] font-bold',
            d.state === 'missing' || d.state === 'failed'
              ? 'text-slate-400'
              : d.state === 'provisional'
                ? 'text-slate-500'
                : 'text-slate-800',
          )}
          title={STATE_LABEL[d.state] ?? undefined}
        >
          {fmtDateNumeric(d.date)}
        </span>
      ),
      csv: (d) => fmtDateNumeric(d.date) + STATE_SUFFIX[d.state],
      title: (d) => STATE_LABEL[d.state] ?? undefined,
    },
    // Количество идёт первым: «сколько продали» — отправная точка, с которой
    // начинают читать день, а деньги уже объясняют, во что это обошлось.
    {
      key: 'sold',
      label: 'Продано, шт',
      group: 'Количество',
      value: (d) => (
        <span className="tnum text-[11px] font-semibold text-slate-700">{fmtNum(d.soldQty)}</span>
      ),
      csv: (d) => d.soldQty,
    },
    {
      key: 'returned',
      label: 'Возвращено, шт',
      group: 'Количество',
      value: (d) => (
        <span className="tnum text-[11px] font-semibold text-slate-700">
          {fmtNum(d.returnedQty)}
        </span>
      ),
      csv: (d) => d.returnedQty,
    },
    money('sales', 'Выручка', 'Продажи', (d) => d.sales),
    money('returns', 'Возвраты', 'Продажи', (d) => d.returns, { negative: true }),
    money('partners', 'Бонусы', 'Продажи', (d) => d.partners),
    // Итог группы тоже называется «Итого», а не «Продажи»: одноимённая колонка
    // внутри одноимённой группы читалась как «Продажи продаж» и не отвечала на
    // вопрос «это сумма или ещё один показатель».
    money('income', 'Итого', 'Продажи', (d) => d.income, {
      strong: true,
      csvLabel: 'Продажи, всего',
    }),
    ...expenseColumns,
    money('expenses', 'Итого', 'Расходы', (d) => d.expenses, {
      negative: true,
      strong: true,
      csvLabel: 'Расходы, всего',
    }),
    {
      key: 'profit',
      label: 'Прибыль, ₽',
      // Группа называется так же, как колонка: «Итог» из одного столбца
      // занимал место в шапке, ничего не группируя.
      group: 'Прибыль',
      value: (d) => (
        // Прибыль без цветовой оценки: зелёный читался как «хорошо», хотя
        // прибыль в 200 ₽ при расходах в 500 000 — не достижение. Отрицательное
        // значение по-прежнему с минусом, и это единственное, что нужно видеть.
        <span className="tnum text-[11px] font-bold text-slate-800">
          {d.profit < 0 && '−'}
          {fmtMoneyBare(Math.abs(d.profit))}
        </span>
      ),
      csv: (d) => d.profit,
    },
    // Колонка «К выплате» убрана намеренно.
    //
    // <p>OZON отдаёт `total_amount` по дню, но по определению он равен
    // `income − expenses`, то есть той же прибыли: на живых данных за 01.10.2026
    // 19 480 − 24 748,65 = −5 268,65 и приходило ровно −5 268,65. Две колонки с
    // одинаковыми числами заставляли искать между ними разницу.
    //
    // <p>Отличаются они только при отборе по товарам: там выплату не разделить и
    // она приходит `null`. Таблицу открывают, чтобы смотреть отбор, — и колонка
    // была прочерком.
  ]
}

/** Выгружает дневную таблицу в CSV: шапка, строки дней, итог. */
export function exportDailyCsv(days: DayFinance[]): void {
  const columns = buildColumns(days)
  const totals = totalsOf(days)
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

  const header = columns.map((c) => (c.csvLabel ?? c.label).replace(/,\s*₽$/, ''))
  const body = days.map((d) =>
    columns.map((c) => {
      const value = c.csv?.(d)
      // Округляем уже в выгрузке, а не при сборке колонок: в интерфейсе суммы
      // идут через формат, а в CSV — сырые, и без округления там остался бы
      // хвост плавающей точки. Количество — целое, ему округление не вредит.
      if (typeof value === 'number') return round2(value)
      if (value !== undefined) return value
      const rendered = c.value(d)
      return typeof rendered === 'string' || typeof rendered === 'number' ? rendered : ''
    }),
  )
  const totalRow = columns.map((c, i) => {
    if (i === 0) return 'Итого'
    const value = round2(totalOf(c.key))
    return isNegative(c.key) ? -Math.abs(value) : value
  })
  downloadCsv(safeFilename(['Обзор', 'аналитика-по-дням']), toCsv([header, ...body, totalRow]))
}

/**
 * Аналитика по дням: таблица, которая раньше была вторым режимом графика.
 *
 * <p>Колонки сгруппированы по смыслу — продажи, расходы, итог, количество, — поэтому
 * в шапке два уровня: группа и колонки внутри неё. Одиннадцать колонок подряд без
 * групп читаются как список чисел, а не как отчёт.
 *
 * <p>Суммы — с копейками: сумма колонок обязана совпадать с итогом, а округлённые до
 * рубля слагаемые этого не делают.
 */
export default function DailyFinanceTable({
  days,
  page,
  onPageChange,
}: {
  days: DayFinance[]
  page: number
  onPageChange: (page: number) => void
}) {
  const columns = useMemo(() => buildColumns(days), [days])
  const totals = useMemo(() => totalsOf(days), [days])

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

  return (
    <DataTable
      rows={days}
      columns={columns}
      rowKey={(d) => d.date}
      // Своя прокрутка, а не прокрутка тела модалки: иначе шапка уезжает
      // вместе с содержимым, и в списке на сотню дней не видно, что за колонка.
      maxHeight="calc(100vh - 19rem)"
      emptyText="За выбранный период дней нет"
      pageSize={PAGE_SIZE}
      page={page}
      onPageChange={onPageChange}
      footer={{
        label: 'Итого',
        value: (c) => {
          const key = c.key
          if (key === 'sold' || key === 'returned') {
            return (
              <span className="tnum text-[11px] font-extrabold text-slate-900">
                {fmtNum(totalOf(key))}
              </span>
            )
          }
          const value = totalOf(key)
          return (
            <span className="tnum text-[11px] font-extrabold text-slate-900">
              {isNegative(key) && value !== 0 && '−'}
              {fmtMoneyBare(Math.abs(value))}
            </span>
          )
        },
      }}
    />
  )
}
