import type { DayFinance, FinanceTotals } from '../types'

// ─── Деньги ──────────────────────────────────────────────────────────────────

const nf = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 })

/** 1248560 → "1 248 560 ₽" */
export function fmtMoney(n: number): string {
  return `${nf.format(Math.round(n))} ₽`
}

/** 1248560 → "1 248 560" (без знака валюты, для узких мест) */
export function fmtNum(n: number): string {
  return nf.format(Math.round(n))
}

/** Компактный формат для оси графика: 300 000 ₽ */
export function fmtAxis(n: number): string {
  return `${nf.format(Math.round(n))} ₽`
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
export const WEEKDAYS_SHORT = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб']

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

/** "2026-09-18" → "ср" */
export function weekdayShort(iso: string): string {
  return WEEKDAYS_SHORT[fromISO(iso).getDay()]
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

/** Количество дней в диапазоне включительно */
export function daysInRange(from: string, to: string): number {
  return Math.round((fromISO(to).getTime() - fromISO(from).getTime()) / 86400000) + 1
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
    otherByType: [],
    expenses: 0, profit: 0, soldQty: 0, returnedQty: 0,
  }
  const byType = new Map<string, number>()
  for (const d of days) {
    t.soldQty += d.soldQty
    t.returnedQty += d.returnedQty
    for (const o of d.otherByType) byType.set(o.name, (byType.get(o.name) ?? 0) + o.amount)
    t.sales += d.sales
    t.returns += d.returns
    t.partners += d.partners
    t.income += d.income
    t.commission += d.commission
    t.logistics += d.logistics
    t.other += d.other
    t.expenses += d.expenses
    t.profit += d.profit
  }
  t.otherByType = [...byType.entries()]
    .map(([name, amount]) => ({ name, amount }))
    .sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount))
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

export function clamp(v: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, v))
}
