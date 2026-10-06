import { useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { CalendarDays, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '../utils/cn'
import {
  addDaysISO, fmtDateLong, fmtRange, fromISO, isToday, MONTHS_NOM, todayISO, toISO,
} from '../utils/format'

interface Preset {
  label: string
  get: () => { from: string; to: string }
}

function buildPresets(): Preset[] {
  const t = todayISO()
  return [
    { label: 'Сегодня', get: () => ({ from: t, to: t }) },
    { label: 'Вчера', get: () => ({ from: addDaysISO(t, -1), to: addDaysISO(t, -1) }) },
    { label: '7 дней', get: () => ({ from: addDaysISO(t, -6), to: t }) },
    { label: '30 дней', get: () => ({ from: addDaysISO(t, -29), to: t }) },
    { label: '90 дней', get: () => ({ from: addDaysISO(t, -89), to: t }) },
    { label: 'Пол года', get: () => ({ from: addDaysISO(t, -179), to: t }) },
    {
      label: 'Этот месяц',
      get: () => {
        const d = fromISO(t)
        return { from: toISO(new Date(d.getFullYear(), d.getMonth(), 1)), to: t }
      },
    },
    {
      label: 'Прошлый месяц',
      get: () => {
        const d = fromISO(t)
        const first = new Date(d.getFullYear(), d.getMonth() - 1, 1)
        const last = new Date(d.getFullYear(), d.getMonth(), 0)
        return { from: toISO(first), to: toISO(last) }
      },
    },
  ]
}

const WEEK_HEADER = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']

/** Сетка месяца: 6 недель × 7 дней (неделя с понедельника) */
function monthGrid(year: number, month: number): string[][] {
  const first = new Date(year, month, 1)
  const dow = (first.getDay() + 6) % 7 // 0 = понедельник
  const start = new Date(year, month, 1 - dow)
  const weeks: string[][] = []
  for (let w = 0; w < 6; w++) {
    const week: string[] = []
    for (let d = 0; d < 7; d++) {
      week.push(toISO(start))
      start.setDate(start.getDate() + 1)
    }
    weeks.push(week)
  }
  return weeks
}

function MonthView({
  year,
  month,
  lo,
  hi,
  edgeStart,
  edgeEnd,
  onDayClick,
  onDayHover,
  minDate,
  maxDate,
}: {
  minDate?: string
  maxDate?: string
  year: number
  month: number
  lo: string | null
  hi: string | null
  edgeStart: string | null
  edgeEnd: string | null
  onDayClick: (iso: string) => void
  onDayHover: (iso: string | null) => void
}) {
  const weeks = monthGrid(year, month)
  const monthPrefix = `${year}-${String(month + 1).padStart(2, '0')}`

  return (
    <div className="w-[254px] shrink-0">
      <div className="pb-2 text-center text-[13px] font-bold text-slate-800">
        {MONTHS_NOM[month]} <span className="font-semibold text-slate-400">{year}</span>
      </div>
      <div className="grid grid-cols-7 gap-y-0.5">
        {WEEK_HEADER.map((w) => (
          <div key={w} className="pb-1 text-center text-[10px] font-bold uppercase tracking-wider text-slate-400">
            {w}
          </div>
        ))}
        {weeks.flat().map((iso) => {
          // Дни соседних месяцев не рисуются вовсе. Раньше они показывались серым
          // и оставались наводимыми: наведение на чужую дату меняло превью диапазона
          // так, будто оно относится к этому месяцу, хотя выбрать её было можно.
          // Пустая ячейка сохраняет высоту сетки — оба месяца в ряд остаются
          // одинаковой высоты, даже если в одном из них пять недель, а в другом шесть.
          if (!iso.startsWith(monthPrefix)) {
            return <div key={iso} className="h-8" aria-hidden="true" />
          }

          const isEdgeS = iso === edgeStart
          const isEdgeE = iso === edgeEnd
          const inRange = lo && hi && iso > lo && iso < hi
          const today = isToday(iso)

          return (
            <div
              key={iso}
              className={cn(
                'relative flex h-8 items-center justify-center',
                inRange && 'bg-brand-50',
                isEdgeS && hi && edgeStart !== edgeEnd && 'rounded-l-full bg-brand-50',
                isEdgeE && lo && edgeStart !== edgeEnd && 'rounded-r-full bg-brand-50',
                edgeStart !== null && edgeEnd === null && iso > edgeStart && iso <= (hi ?? '') && 'bg-brand-50/70',
              )}
            >
              <button
                disabled={Boolean((minDate && iso < minDate) || (maxDate && iso > maxDate))}
                onClick={() => onDayClick(iso)}
                onMouseEnter={() => onDayHover(iso)}
                className={cn(
                  'relative z-10 flex h-7 w-7 items-center justify-center rounded-full text-[12px] font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent',
                  'text-slate-600 hover:bg-slate-100',
                  (isEdgeS || isEdgeE) && 'bg-brand-600 !text-white shadow-md shadow-brand-600/40 hover:!bg-brand-700',
                  today && !isEdgeS && !isEdgeE && 'ring-1 ring-inset ring-brand-400',
                )}
              >
                {Number(iso.slice(8))}
              </button>
            </div>
          )
        })}
      </div>
    </div>
  )
}

/** Ранние начисления OZON не отдаёт — дальше этой даты выбирать бессмысленно */
export const MIN_DATE = '2022-01-01'

export default function DateRangePicker({
  from,
  to,
  onChange,
}: {
  from: string
  to: string
  onChange: (r: { from: string; to: string }) => void
}) {
  const maxDate = todayISO()
  const minDate = MIN_DATE
  const [open, setOpen] = useState(false)
  const [tempFrom, setTempFrom] = useState<string | null>(null)
  const [tempTo, setTempTo] = useState<string | null>(null)
  const [hover, setHover] = useState<string | null>(null)
  // левый видимый месяц
  const [view, setView] = useState(() => new Date(fromISO(to).getFullYear(), fromISO(to).getMonth() - 1, 1))
  const ref = useRef<HTMLDivElement>(null)
  const presets = buildPresets()

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const openPicker = () => {
    setTempFrom(from)
    setTempTo(to)
    setHover(null)
    const toD = fromISO(to)
    setView(new Date(toD.getFullYear(), toD.getMonth() - 1, 1))
    setOpen(true)
  }

  const shiftView = (delta: number) =>
    setView((v) => new Date(v.getFullYear(), v.getMonth() + delta, 1))

  const handleDayClick = (iso: string) => {
    if (!tempFrom || (tempFrom && tempTo)) {
      setTempFrom(iso)
      setTempTo(null)
    } else if (iso >= tempFrom) {
      setTempTo(iso)
    } else {
      setTempTo(tempFrom)
      setTempFrom(iso)
    }
  }

  // эффективные границы (учитываем hover-превью при выборе конца)
  const effEnd = tempTo ?? (tempFrom ? hover : null)
  const lo = tempFrom && effEnd ? (tempFrom <= effEnd ? tempFrom : effEnd) : null
  const hi = tempFrom && effEnd ? (tempFrom <= effEnd ? effEnd : tempFrom) : null
  const complete = Boolean(tempFrom && tempTo)

  const rightMonth = new Date(view.getFullYear(), view.getMonth() + 1, 1)

  return (
    <div ref={ref} className="relative">
      <button
        onClick={openPicker}
        className={cn(
          'flex items-center gap-2.5 rounded-xl border bg-white py-2 pl-3 pr-2.5 text-[13px] font-bold text-slate-700 transition-all',
          open ? 'border-brand-300 shadow-sm ring-4 ring-brand-500/10' : 'border-slate-200 hover:border-slate-300 hover:shadow-sm',
        )}
      >
        <CalendarDays size={16} className="text-brand-600" />
        <span className="whitespace-nowrap">{fmtRange(from, to)}</span>
        <ChevronDown size={15} className={cn('text-slate-400 transition-transform', open && 'rotate-180')} />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -6, scale: 0.985 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -6, scale: 0.985 }}
            transition={{ duration: 0.16, ease: 'easeOut' }}
            onMouseLeave={() => setHover(null)}
            className="absolute right-0 top-full z-50 mt-2 max-w-[calc(100vw-1.5rem)] origin-top-right overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-2xl shadow-slate-900/10"
          >
            <div className="scroll-slim flex max-w-full overflow-x-auto">
              {/* Пресеты */}
              <div className="hidden w-[128px] shrink-0 flex-col gap-0.5 border-r border-slate-100 p-2 sm:flex">
                {presets.map((p) => {
                  const r = p.get()
                  const active = r.from === from && r.to === to
                  return (
                    <button
                      key={p.label}
                      onClick={() => {
                        onChange(r)
                        setOpen(false)
                      }}
                      className={cn(
                        'rounded-lg px-3 py-2 text-left text-[12px] font-semibold transition-colors',
                        active ? 'bg-brand-50 text-brand-700' : 'text-slate-500 hover:bg-slate-50 hover:text-slate-800',
                      )}
                    >
                      {p.label}
                    </button>
                  )
                })}
              </div>

              {/* Календарь */}
              <div className="relative p-4">
                <button
                  onClick={() => shiftView(-1)}
                  className="absolute left-3 top-3.5 flex h-7 w-7 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                >
                  <ChevronLeft size={16} />
                </button>
                <button
                  onClick={() => shiftView(1)}
                  className="absolute right-3 top-3.5 flex h-7 w-7 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                >
                  <ChevronRight size={16} />
                </button>
                <div className="flex gap-6">
                  <MonthView
                    minDate={minDate} maxDate={maxDate}
                    year={view.getFullYear()}
                    month={view.getMonth()}
                    lo={lo} hi={hi}
                    edgeStart={tempFrom} edgeEnd={effEnd}
                    onDayClick={handleDayClick}
                    onDayHover={setHover}
                  />
                  <MonthView
                    minDate={minDate} maxDate={maxDate}
                    year={rightMonth.getFullYear()}
                    month={rightMonth.getMonth()}
                    lo={lo} hi={hi}
                    edgeStart={tempFrom} edgeEnd={effEnd}
                    onDayClick={handleDayClick}
                    onDayHover={setHover}
                  />
                </div>

                {/* Футер */}
                <div className="mt-3 flex items-center justify-between gap-3 border-t border-slate-100 pt-3">
                  {/* После первой даты показывается сама дата, а не только просьба доделать
                      выбор. Подсказка про окончание уходит в подпись меньшим
                      шрифтом: дата главная, а подсказка не должна занимать её место. */}
                  <div className="text-[12px] font-semibold text-slate-500">
                    {complete ? (
                      <span className="text-slate-800">{fmtRange(tempFrom!, tempTo!)}</span>
                    ) : tempFrom ? (
                      <span className="flex flex-wrap items-baseline gap-x-2">
                        <span className="font-bold text-slate-800">{fmtDateLong(tempFrom)}</span>
                        <span className="text-[11px] font-medium text-slate-400">
                          выберите дату окончания
                        </span>
                      </span>
                    ) : (
                      'Выберите период'
                    )}
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={() => setOpen(false)}
                      className="rounded-xl px-3.5 py-2 text-[12px] font-bold text-slate-500 transition-colors hover:bg-slate-100"
                    >
                      Отмена
                    </button>
                    <button
                      disabled={!complete}
                      onClick={() => {
                        onChange({ from: tempFrom!, to: tempTo! })
                        setOpen(false)
                      }}
                      className="rounded-xl bg-brand-600 px-4 py-2 text-[12px] font-bold text-white shadow-sm shadow-brand-600/30 transition-all hover:bg-brand-700 active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
                    >
                      Применить
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
