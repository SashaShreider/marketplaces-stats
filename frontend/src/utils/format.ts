import type { DayFinance, FinanceTotals } from '@/types'

// ─── Деньги ──────────────────────────────────────────────────────────────────

const nf = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 })
const nf2 = new Intl.NumberFormat('ru-RU', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})
const nf1 = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 })

/**
 * Типографский минус U+2212 вместо дефиса, который ставит Intl.
 *
 * Дефис по длине равен цифре и визуально короче, чем знак вычитания, поэтому в
 * колонке чисел минусы выглядят неровно. Раньше знак подставлялся вручную и был
 * правильным, а числа без знака проходили через модуль — из-за чего убыток
 * показывался как положительная сумма.
 */
const MINUS = '\u2212'

/**
 * Округляет, не оставляя отрицательного нуля.
 *
 * Вычитание даёт `-0`, и `Intl` печатает его как «-0»: строка расходов показывала
 * «−0 ₽» там, где расходов не было. Знак у нуля не несёт смысла, поэтому ноль —
 * всегда ноль. Заодно уходит «-0» из чисел вида −0.4, которые округляются в ноль.
 */
function safeRound(n: number): number {
  const rounded = Math.round(n)
  return rounded === 0 ? 0 : rounded
}

/** Ставит перед знаком типографский минус там, где Intl дал дефис. */
function withTypographicMinus(s: string): string {
  return s.replace(/^-/, MINUS)
}

/** 1248560 → "1 248 560 ₽", -5000 → "−5 000 ₽" */
export function fmtMoney(n: number): string {
  return `${withTypographicMinus(nf.format(safeRound(n)))} ₽`
}

/** 1248560 → "1 248 560" (без знака валюты, для узких мест) */
export function fmtNum(n: number): string {
  return withTypographicMinus(nf.format(safeRound(n)))
}

/**
 * Деньги с копейками: "−12 879,33 ₽".
 *
 * <p>Отдельная функция, а не замена {@link fmtMoney}: суммы в карточках и на графике
 * округляются до рубля намеренно — там важно соотношение, а не копейка. В отчёте по
 * дням и в детализации расходов копейки нужны: сумма статей обязана совпадать с
 * итогом, а рубль её не сходил бы.
 */
export function fmtMoney2(n: number): string {
  const rounded = Math.round(n * 100) / 100
  return `${withTypographicMinus(nf2.format(rounded === 0 ? 0 : rounded))} ₽`
}

/**
 * Деньги с копейками без знака валюты: «−12 879,33».
 *
 * <p>Для таблиц, где «₽» стоит один раз в шапке колонки: повторять его в каждой
 * ячейке — значит отнимать ширину у цифр.
 */
export function fmtMoneyBare(n: number): string {
  const rounded = Math.round(n * 100) / 100
  return withTypographicMinus(nf2.format(rounded === 0 ? 0 : rounded))
}

/** Количество с копейками не бывает — только целое; оставлено для симметрии таблиц. */
export function fmtNum2(n: number): string {
  return withTypographicMinus(nf2.format(Math.round(n * 100) / 100))
}

/** Проценты: 0.1234 → "12,3 %". */
export function fmtPercent(share: number): string {
  if (!Number.isFinite(share)) return '—'
  return `${nf1.format(share * 100)} %`
}

/** Компактный формат для оси графика: 300 000 ₽ */
export function fmtAxis(n: number): string {
  return `${nf.format(Math.round(n))} ₽`
}

/**
 * Прибыль: продажи за вычетом расходов.
 *
 * <p>Оба аргумента приходят положительными — так их отдаёт бэкенд, где расходы
 * считаются модулем, — поэтому вычитаем без знака. Считаем в одной функции,
 * потому что число показывается в двух местах, и разница в знаке между ними
 * выглядела бы как ошибка в одном из них.
 */
export function grossProfit(income: number, expenses: number): number {
  return (income ?? 0) - Math.abs(expenses ?? 0)
}

// ─── Русские названия ────────────────────────────────────────────────────────

export const MONTHS_GEN = [
  'января', 'февраля', 'марта', 'апреля', 'мая', 'июня',
  'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря',
]
export const MONTHS_NOM = [
  'Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь',
  'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь',
]
export const MONTHS_SHORT = [
  'янв', 'фев', 'мар', 'апр', 'мая', 'июн',
  'июл', 'авг', 'сен', 'окт', 'ноя', 'дек',
]

// ─── Форматирование дат ──────────────────────────────────────────────────────

/** "2026-09-18" → "18 сентября 2026" */
export function fmtDateLong(iso: string): string {
  const d = fromISO(iso)
  return `${d.getDate()} ${MONTHS_GEN[d.getMonth()]} ${d.getFullYear()}`
}

/** "2026-09-18" → "18 сен" */
export function fmtDayShort(iso: string): string {
  const d = fromISO(iso)
  return `${d.getDate()} ${MONTHS_SHORT[d.getMonth()]}`
}

/**
 * "2026-09-18" → "18.09.2026".
 *
 * <p>Числовая дата для таблицы: день недели и месяц словами занимают место, а
 * сравнивать и выгружать такие даты удобнее числами. Формат задан явно, а не через
 * `toLocaleDateString`: тот зависит от локали браузера и на машине с английскими
 * настройками выдал бы 9/18/2026.
 */
export function fmtDateNumeric(iso: string): string {
  const d = fromISO(iso)
  const day = String(d.getDate()).padStart(2, '0')
  const month = String(d.getMonth() + 1).padStart(2, '0')
  return `${day}.${month}.${d.getFullYear()}`
}

/** Компактный период: "01.08.2026 — 29.10.2026" */
export function fmtRangeShort(from: string, to: string): string {
  return from === to ? fmtDateNumeric(from) : `${fmtDateNumeric(from)} — ${fmtDateNumeric(to)}`
}

/** Диапазон вида "1 сентября — 30 сентября 2026" */
export function fmtRange(from: string, to: string): string {
  const a = fromISO(from)
  const b = fromISO(to)
  if (a.getTime() === b.getTime()) return fmtDateLong(from)
  if (a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth()) {
    return `${a.getDate()} ${MONTHS_GEN[a.getMonth()]} — ${b.getDate()} ${MONTHS_GEN[b.getMonth()]} ${b.getFullYear()}`
  }
  if (a.getFullYear() === b.getFullYear()) {
    return `${a.getDate()} ${MONTHS_GEN[a.getMonth()]} — ${b.getDate()} ${MONTHS_GEN[b.getMonth()]} ${b.getFullYear()}`
  }
  return `${fmtDateLong(from)} — ${fmtDateLong(to)}`
}

// ─── Арифметика дат (local, ISO "YYYY-MM-DD") ────────────────────────────────

export function toISO(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export function fromISO(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function addDaysISO(iso: string, days: number): string {
  const d = fromISO(iso)
  d.setDate(d.getDate() + days)
  return toISO(d)
}

export function todayISO(): string {
  return toISO(new Date())
}

export function lastNDays(n: number): { from: string; to: string } {
  const to = todayISO()
  return { from: addDaysISO(to, -(n - 1)), to }
}

export function isToday(iso: string): boolean {
  return iso === todayISO()
}

// ─── Агрегация ───────────────────────────────────────────────────────────────

export function sumDays(days: DayFinance[]): FinanceTotals {
  const t: FinanceTotals = {
    sales: 0, returns: 0, partners: 0, income: 0,
    commission: 0, logistics: 0, other: 0,
    expenses: 0, profit: 0, soldQty: 0, returnedQty: 0,
    payout: 0,
  }
  // Выплату складываем только пока она известна у всех дней. Если хоть один день
  // пришёл без неё (отчёт по части товаров), сумма превратилась бы в «выплату по
  // тем дням, что посчитались», — а это утверждение, которого никто не проверял.
  let payout: number | null = 0
  for (const d of days) {
    t.soldQty += d.soldQty
    t.returnedQty += d.returnedQty
    t.sales += d.sales
    t.returns += d.returns
    t.partners += d.partners
    t.income += d.income
    t.commission += d.commission
    t.logistics += d.logistics
    t.other += d.other
    t.expenses += d.expenses
    t.profit += d.profit
    payout = payout === null || d.payout === null ? null : payout + d.payout
  }
  t.payout = payout
  return t
}

// ─── Прочее ──────────────────────────────────────────────────────────────────

/** "2026-10-05T04:00:38Z" → "5 октября 2026, 07:00" */
export function fmtDateTime(iso: string): string {
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${d.getDate()} ${MONTHS_GEN[d.getMonth()]} ${d.getFullYear()}, ${hh}:${mm}`
}

/** «Красивый» максимум для оси Y: 1 / 2 / 2.5 / 5 × 10^k */
export function niceMax(v: number): number {
  if (v <= 0) return 1
  const p = Math.pow(10, Math.floor(Math.log10(v)))
  const f = v / p
  const nf2 = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10
  return nf2 * p
}

/**
 * Ось с круглыми делениями: границы и значения линий сетки.
 *
 * <p>Считается от фактического максимума данных, а не от заранее выбранной
 * шкалы. Раньше максимум брался из «красивого» ряда 50 000 / 100 000, и при
 * дневных суммах около 8 000 график занимал нижнюю десятую часть: линия шла по
 * низу, а верхние три четверти оставались пустыми.
 *
 * <p>Шаг подбирается из ряда 1 / 2 / 2.5 / 5 × 10^k так, чтобы линий сетки было
 * около {@code intervals}: округление «вверх до ближайшего круглого» давало всего
 * два деления (0, 20 000, 40 000) — по такому графику не прочитать, на каком
 * уровне день между отметками.
 *
 * @param min       минимум данных; отрицательный (убыток) расширяет ось вниз
 * @param max       максимум данных
 * @param intervals желаемое число промежутков между линиями сетки
 */
export function niceScale(
  min: number,
  max: number,
  intervals = 4,
): { min: number; max: number; ticks: number[] } {
  const lo = Math.min(0, min)
  const hi = Math.max(0, max)
  if (!Number.isFinite(hi) || !Number.isFinite(lo) || hi - lo < 1e-9) {
    return { min: 0, max: 1, ticks: [0, 1] }
  }
  const target = Math.max(1, Math.round(intervals))

  /**
   * Шаг сетки под верхнюю часть оси.
   *
   * <p>Считается по положительной части, а не по всему размаху: иначе один
   * убыточный день на сотню рублей при продажах в десятки тысяч удваивал шаг, и
   * вместо отметок через 5 000 появлялись через 10 000 — график терял точность
   * из-за дня, который на нём неразличим.
   */
  const top = (step: number) => Math.ceil(hi / step) * step

  const p = Math.pow(10, Math.floor(Math.log10(hi / target)))
  const candidates: number[] = []
  for (const decade of [0.1, 1, 10]) {
    for (const mult of [1, 2, 2.5, 5]) candidates.push(mult * p * decade)
  }
  candidates.sort((a, b) => a - b)

  /**
   * Шаг, дающий ближайшее к `target` число промежутков сверху.
   *
   * <p>Важно брать не «первый подходящий», а именно ближайший: сетка должна
   * быть плотной (0, 10 000, 20 000, 30 000, 40 000), но не превращаться в
   * частокол из двух десятков линий.
   */
  let step = candidates[0]
  let bestDiff = Infinity
  for (const candidate of candidates) {
    const count = Math.round(top(candidate) / candidate)
    const diff = Math.abs(count - target)
    if (diff < bestDiff) {
      bestDiff = diff
      step = candidate
    }
  }

  /**
   * Отрицательная часть — целыми шагами вниз, и только когда убыток на неё
   * заслуживает.
   *
   * <p>Полшага ниже нуля даёт одну «минусовую» отметку: её достаточно, чтобы
   * убыток читался как убыток. Раздувать ось на весь −10 000 из-за дня в −200
   * бессмысленно — там разница всё равно не видна, а места уходит четверть.
   */
  const stepsDown = lo < 0 ? Math.ceil(Math.abs(lo) / step) : 0
  const from = -stepsDown * step
  const to = top(step)

  const count = Math.round((to - from) / step)
  const ticks: number[] = []
  // Считаем по индексу, а не накоплением: иначе ошибка плавающей точки даёт
  // подпись «7 999,999» вместо «8 000».
  for (let i = 0; i <= count; i++) {
    ticks.push(Math.round((from + i * step) * 1e6) / 1e6)
  }
  return { min: from, max: to, ticks }
}

export function clamp(v: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, v))
}
