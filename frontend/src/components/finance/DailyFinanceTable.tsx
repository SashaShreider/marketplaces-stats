import { useMemo } from 'react'
import type { DayFinance, DayState, ExpenseBreakdown, ExpenseCategory } from '@/types'
import DataTable, { type DataColumn } from '@/components/ui/DataTable'
import { EXPENSE_ORDER, orderedGroups } from '@/components/finance/SummaryCards'
import { cn } from '@/utils/cn'
import { downloadCsv, safeFilename, toCsv } from '@/utils/csv'
import { MONTHS_NOM, fmtDateNumeric, fmtMoneyBare, fmtNum } from '@/utils/format'

/** Сколько строк на странице: месяц-полтора, чтобы прокрутка не уставала. */
export const PAGE_SIZE = 90

/**
 * Строка таблицы: день периода или свёрнутый месяц.
 *
 * <p>`label` есть только у месячной строки — это её подпись («Октябрь 2026» или
 * период, если месяц неполный). У дневной строки его нет, и подпись берётся из
 * даты: так колонки и выгрузка остаются одни на оба вида, без ветвления по типу
 * строки.
 */
export type TableRow = DayFinance & { label?: string }


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

/** Подпись строки: у месяца — «Октябрь 2026» или период, у дня — дата. */
function rowLabel(d: TableRow): string {
  return d.label ?? fmtDateNumeric(d.date)
}

/**
 * Подпись месяца: «Октябрь 2026» у полного, «01.05.2026 — 15.05.2026» у неполного.
 *
 * <p>Полным считается месяц, у которого в таблице есть все его дни. Иначе сумма
 * выглядела бы месячной, хотя посчитана за кусок: «Октябрь 2026» с пятнадцатью
 * днями — это не октябрь, и подписывать его так нельзя.
 */
function monthLabel(monthKey: string, monthDays: DayFinance[]): string {
  const [year, month] = monthKey.split('-').map(Number)
  const daysInMonth = new Date(year, month, 0).getDate()
  if (monthDays.length === daysInMonth) {
    return `${MONTHS_NOM[month - 1]} ${year}`
  }
  const first = monthDays[0].date
  const last = monthDays[monthDays.length - 1].date
  return first === last ? fmtDateNumeric(first) : `${fmtDateNumeric(first)} — ${fmtDateNumeric(last)}`
}

/**
 * Свёртка дней периода в месяцы.
 *
 * <p>Строка месяца — сумма дней, а не среднее: вопрос «сколько за месяц» задают
 * деньгами. Статьи расходов складываются так же, как деньги, — иначе «Итого» в
 * подробном виде не сошлось бы со своими же колонками.
 *
 * <p>Состояние месяца — наихудшее из состояний его дней: если хоть один день не
 * загружен, сумма за месяц неполная, и это важнее, чем «уточняется» у соседнего.
 * По той же причине подпись неполного месяца содержит период.
 */
export function groupByMonth(days: DayFinance[]): TableRow[] {
  const byMonth = new Map<string, DayFinance[]>()
  for (const day of days) {
    const key = day.date.slice(0, 7)
    const list = byMonth.get(key)
    if (list) list.push(day)
    else byMonth.set(key, [day])
  }

  const rows: TableRow[] = []
  for (const [key, monthDays] of byMonth) {
    const label = monthLabel(key, monthDays)
    const sum = {
      sales: 0, returns: 0, partners: 0, income: 0,
      commission: 0, logistics: 0, other: 0,
      expenses: 0, profit: 0, soldQty: 0, returnedQty: 0,
    }
    const groups = new Map<ExpenseCategory, { label: string; amount: number }>()
    let state: DayState = 'ok'

    for (const d of monthDays) {
      sum.sales += d.sales
      sum.returns += d.returns
      sum.partners += d.partners
      sum.income += d.income
      sum.commission += d.commission
      sum.logistics += d.logistics
      sum.other += d.other
      sum.expenses += d.expenses
      sum.profit += d.profit
      sum.soldQty += d.soldQty
      sum.returnedQty += d.returnedQty
      for (const g of d.expenseBreakdown?.groups ?? []) {
        const current = groups.get(g.category)
        groups.set(g.category, {
          label: g.label,
          amount: (current?.amount ?? 0) + g.amount,
        })
      }
      // Худшее состояние месяца: «ошибка» важнее «не загружен», а «не загружен»
      // важнее «уточняется». Порядок задан явно, а не порядком дней.
      if (d.state === 'failed') state = 'failed'
      else if (d.state === 'missing' && state !== 'failed') state = 'missing'
      else if (d.state === 'provisional' && state === 'ok') state = 'provisional'
    }

    const expenseBreakdown: ExpenseBreakdown = {
      groups: [...groups].map(([category, g]) => ({ category, label: g.label, amount: g.amount, items: [] })),
      total: [...groups.values()].reduce((s, g) => s + g.amount, 0),
    }

    rows.push({
      ...sum,
      // Даты у месяца нет; полем остаётся первое число — по нему строится
      // сортировка и ключ строки, и по нему же выгружается месяц в CSV.
      date: `${key}-01`,
      state,
      payout: null,
      expenseBreakdown,
      label,
    })
  }
  return rows
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
          {rowLabel(d)}
        </span>
      ),
      csv: (d) => rowLabel(d) + STATE_SUFFIX[d.state],
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

/**
 * Выгружает таблицу в CSV: шапка, строки, итог.
 *
 * <p>Строки задаются снаружи, а не берутся из `days`: в месячном виде выгружать
 * надо месяцы, и фиктивная «дата» у месячной строки в файл попасть не должна.
 *
 * @param rows    строки выгрузки: дни или месяцы
 * @param totals  дни периода — по ним считается итог, он одинаков в обоих видах
 * @param compact выгружать компактный набор колонок вместо подробного
 */
export function exportFinanceCsv(
  rows: TableRow[],
  totals: DayFinance[],
  compact = false,
): void {
  const columns = compact ? buildCompactColumns() : buildColumns(totals)
  const sum = totalsOf(totals)
  const totalOf = (key: string): number => {
    switch (key) {
      case 'sales': return sum.sales
      case 'returns': return sum.returns
      case 'partners': return sum.partners
      case 'income': return sum.income
      case 'expenses': return sum.expenses
      case 'profit': return sum.profit
      case 'sold': return sum.sold
      case 'returned': return sum.returned
      default: {
        const category = EXPENSE_ORDER.find((c) => c.toLowerCase() === key)
        return category ? sumCategory(totals, category) : 0
      }
    }
  }

  const header = columns.map((c) => (c.csvLabel ?? c.label).replace(/,\s*₽$/, ''))
  const body = rows.map((d) =>
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
 * Колонки компактного вида: одна шапка, пять колонок.
 *
 * <p>Подробный вид отвечает «из чего сложились деньги» — там статьи расходов и
 * группы в шапке. Компактный отвечает «сколько за день в целом»: количество,
 * продажи, расходы и прибыль. Группы убраны намеренно: двухуровневая шапка на
 * пяти колонках занимает столько же места, сколько сами данные, а делить в ней
 * нечего — по одной колонке на смысл.
 *
 * <p>Размер текста в ячейках не задан: его держит сама таблица
 * ({@code textSize="large"}), иначе пришлось бы повторять `text-[12.5px]` в
 * каждой колонке.
 *
 * <p>Продажи и расходы здесь — итоговые значения дня (`income`, `expenses`), а не
 * сумма статей: статья может вернуться плюсом, и складывать её с расходами
 * значило бы показать не то, что в подробном виде.
 */
export function buildCompactColumns(): DataColumn<TableRow>[] {
  return [
    {
      key: 'date',
      label: 'Дата',
      align: 'left',
      value: (d) => (
        <span
          className={cn(
            'tnum font-bold',
            d.state === 'missing' || d.state === 'failed'
              ? 'text-slate-400'
              : d.state === 'provisional'
                ? 'text-slate-500'
                : 'text-slate-800',
          )}
          title={STATE_LABEL[d.state] ?? undefined}
        >
          {rowLabel(d)}
        </span>
      ),
      csv: (d) => rowLabel(d) + STATE_SUFFIX[d.state],
      title: (d) => STATE_LABEL[d.state] ?? undefined,
    },
    {
      key: 'sold',
      label: 'Продано, шт',
      value: (d) => (
        <span className="tnum font-semibold text-slate-700">{fmtNum(d.soldQty)}</span>
      ),
      csv: (d) => d.soldQty,
    },
    {
      key: 'returned',
      label: 'Возвращено, шт',
      value: (d) => (
        <span className="tnum font-semibold text-slate-700">
          {fmtNum(d.returnedQty)}
        </span>
      ),
      csv: (d) => d.returnedQty,
    },
    {
      key: 'income',
      label: 'Продажи, ₽',
      value: (d) => (
        <span className="tnum font-extrabold text-slate-900">{fmtMoneyBare(d.income)}</span>
      ),
      csv: (d) => d.income,
    },
    {
      key: 'expenses',
      label: 'Расходы, ₽',
      value: (d) => (
        <span className="tnum font-semibold text-slate-700">
          {d.expenses !== 0 && '−'}
          {fmtMoneyBare(Math.abs(d.expenses))}
        </span>
      ),
      csv: (d) => -Math.abs(d.expenses),
    },
    {
      key: 'profit',
      label: 'Прибыль, ₽',
      value: (d) => (
        <span className="tnum font-bold text-slate-800">
          {d.profit < 0 && '−'}
          {fmtMoneyBare(Math.abs(d.profit))}
        </span>
      ),
      csv: (d) => d.profit,
    },
  ]
}

/**
 * Аналитика по дням: таблица, которая раньше была вторым режимом графика.
 *
 * <p>Колонки сгруппированы по смыслу — продажи, расходы, итог, количество, — поэтому
 * в шапке два уровня: группа и колонки внутри неё. Одиннадцать колонок подряд без
 * групп читаются как список чисел, а не как отчёт.
 *
 * <p>Компактный вид оставляет одну шапку и пять колонок: подробный отвечает «из
 * чего сложились деньги», компактный — «сколько за день в целом».
 *
 * <p>Шаг строк — дни или месяцы: месячная строка это сумма дней, и подпись у неё
 * «Октябрь 2026», а у неполного месяца — его период.
 *
 * <p>Суммы — с копейками: сумма колонок обязана совпадать с итогом, а округлённые до
 * рубля слагаемые этого не делают.
 */
export default function DailyFinanceTable({
  days,
  page,
  onPageChange,
  compact = false,
  byMonth = false,
}: {
  days: DayFinance[]
  page: number
  onPageChange: (page: number) => void
  /** Компактный вид: одна шапка, пять колонок */
  compact?: boolean
  /** Шаг строк: месяцы вместо дней */
  byMonth?: boolean
}) {
  const rows = useMemo(() => (byMonth ? groupByMonth(days) : days), [byMonth, days])
  const columns = useMemo(() => {
    const built = compact ? buildCompactColumns() : buildColumns(days)
    // В месячном виде первая колонка — не дата, а период: «Дата» над «Октябрь
    // 2026» читалась бы как ошибка, а не как сокращение.
    if (!byMonth) return built
    // Подпись неполного месяца — «01.10.2026 — 29.10.2026», а это в полтора раза
    // шире любого числа в таблице. Без нижней границы обрезка съедала конец
    // периода, и две разные строки выглядели одинаково. В компактном виде шрифт
    // крупнее, поэтому и граница шире.
    const monthWidth = compact ? 215 : 190
    return built.map((c, i) => (i === 0 ? { ...c, label: 'Месяц', minWidth: monthWidth } : c))
  }, [compact, byMonth, days])
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
      rows={rows}
      columns={columns}
      rowKey={(d) => d.date}      // Своя прокрутка, а не прокрутка тела модалки: иначе шапка уезжает
      // вместе с содержимым, и в списке на сотню дней не видно, что за колонка.
      maxHeight="calc(100vh - 19rem)"
      emptyText="За выбранный период дней нет"
      pageSize={PAGE_SIZE}
      page={page}
      onPageChange={onPageChange}
      // Компактный вид — крупнее текст: колонок пять, места хватает. Подробный
      // остаётся плотным, иначе тринадцать колонок не влезают по ширине.
      textSize={compact ? 'large' : 'normal'}
      footer={{
        label: 'Итого',
        value: (c) => {
          const key = c.key
          if (key === 'sold' || key === 'returned') {
            // Размер текста не задан: итог наследует его от ячейки таблицы, и в
            // компактном виде он такой же крупный, как строки над ним.
            return <span className="tnum font-extrabold text-slate-900">{fmtNum(totalOf(key))}</span>
          }
          const value = totalOf(key)
          return (
            <span className="tnum font-extrabold text-slate-900">
              {isNegative(key) && value !== 0 && '−'}
              {fmtMoneyBare(Math.abs(value))}
            </span>
          )
        },
      }}
    />
  )
}
