import { useRef, useState } from 'react'
import { motion } from 'framer-motion'
import type { DayFinance } from '@/types'
import { chartScales, tooltipPosition, useChartWidth } from '@/components/finance/barChartBase'
import { clamp, fmtAxis, fmtDateLong, fmtDayShort, fmtMoney, fmtNum, niceScale } from '@/utils/format'
import { cn } from '@/utils/cn'

/** Что можно рисовать на графике: три линии денег и столбцы количества. */
export type SeriesKey = 'income' | 'expenses' | 'profit' | 'qty'

/**
 * Описание показателя: подпись, цвет и откуда брать значение.
 *
 * <p>`money` решает, к какой оси относится показатель: деньги считаются по
 * рублёвой шкале, количество — по шкале штук. Без этого признака пришлось бы
 * держать два списка и следить, чтобы они не разошлись.
 */
export const SERIES: Record<
  SeriesKey,
  {
    label: string
    /** Короткая подпись для тултипа и легенды */
    short: string
    color: string
    money: boolean
    get: (d: DayFinance) => number
  }
> = {
  income: { label: 'Продажи, ₽', short: 'Продажи', color: '#3b6de8', money: true, get: (d) => d.income },
  expenses: { label: 'Расходы, ₽', short: 'Расходы', color: '#f43f5e', money: true, get: (d) => d.expenses },
  profit: { label: 'Прибыль, ₽', short: 'Прибыль', color: '#10b981', money: true, get: (d) => d.profit },
  qty: { label: 'Продажи, шт', short: 'Продажи, шт', color: '#f59e0b', money: false, get: (d) => d.soldQty },
}

/** Порядок в легенде: линии, затем столбцы. */
export const SERIES_ORDER: SeriesKey[] = ['income', 'expenses', 'profit', 'qty']

const H = 340
const PAD_R = 14
const PAD_T = 20
const PAD_B = 34
/** Ширина колонки под рублёвую шкалу. */
const PAD_L_MONEY = 74
/** Ширина колонки под шкалу штук — левее рублёвой, как в кабинетах маркетплейсов. */
const PAD_L_QTY = 46

/**
 * Гладкая линия без выбросов за пределы поля.
 *
 * <p>Кривая Catmull-Rom (то, что было раньше) на резком пике «перелетает»
 * соседние значения: линия уходила ниже нуля и выше верхней отметки шкалы, как
 * будто прибыль или продажи бывают отрицательными в те дни, когда их нет.
 *
 * <p>Здесь монотонная интерполяция Фрича — Карлсона: касательные в точках
 * перегиба обнуляются, и кривая не выходит за коридор между соседними
 * значениями. Это свойство самой формулы, а не подрезка постфактум.
 */
function monotonePath(pts: { x: number; y: number }[]): string {
  const n = pts.length
  if (n === 0) return ''
  if (n === 1) return `M ${pts[0].x} ${pts[0].y}`
  if (n === 2) return `M ${pts[0].x} ${pts[0].y} L ${pts[1].x} ${pts[1].y}`

  const dx: number[] = []
  const slope: number[] = []
  for (let i = 0; i < n - 1; i++) {
    dx.push(pts[i + 1].x - pts[i].x)
    slope.push((pts[i + 1].y - pts[i].y) / (pts[i + 1].x - pts[i].x))
  }

  // Наклон касательной в каждой точке. Ноль на локальном экстремуме — именно он
  // и убирает «горб» над пиком.
  const m: number[] = new Array(n)
  m[0] = slope[0]
  m[n - 1] = slope[n - 2]
  for (let i = 1; i < n - 1; i++) {
    if (slope[i - 1] * slope[i] <= 0) {
      m[i] = 0
    } else {
      const w1 = 2 * dx[i] + dx[i - 1]
      const w2 = dx[i] + 2 * dx[i - 1]
      m[i] = (w1 + w2) / (w1 / slope[i - 1] + w2 / slope[i])
    }
  }

  let d = `M ${pts[0].x} ${pts[0].y}`
  for (let i = 0; i < n - 1; i++) {
    d +=
      ` C ${pts[i].x + dx[i] / 3} ${pts[i].y + (m[i] * dx[i]) / 3},` +
      ` ${pts[i + 1].x - dx[i] / 3} ${pts[i + 1].y - (m[i + 1] * dx[i]) / 3},` +
      ` ${pts[i + 1].x} ${pts[i + 1].y}`
  }
  return d
}

/**
 * Строка детализации в тултипе.
 *
 * Знак берётся из самого числа, а не из отдельного флага: флаг означал бы, что
 * правильность показания зависит от того, не забыл ли его поставить вызывающий.
 * `Math.abs` проглатывал настоящий знак, и убыток в −5 000 ₽ выводился как
 * «5 000 ₽». Нулевой расход с флагом давал «−0 ₽».
 *
 * Отрицательное число показывается с минусом и красным, положительное — тёмным.
 * Что считать расходом, решает вызывающий, передавая число со знаком.
 */
function TipRow({
  label,
  value,
  bold,
  color,
}: {
  label: string
  value: number
  bold?: boolean
  /** Явный цвет: для прибыли он зависит от знака, но задаётся снаружи */
  color?: string
}) {
  return (
    <div className="flex items-center justify-between gap-6 py-[2.5px]">
      <span className={`text-[12px] ${bold ? 'font-bold text-slate-800' : 'font-medium text-slate-500'}`}>
        {label}
      </span>
      <span
        className={`tnum text-[12px] ${bold ? 'font-extrabold' : 'font-medium text-slate-500'}`}
        style={{ color: color ?? undefined }}
      >
        {fmtMoney(value)}
      </span>
    </div>
  )
}

export default function FinanceChart({
  days,
  enabled,
}: {
  days: DayFinance[]
  enabled: Record<SeriesKey, boolean>
}) {
  const wrapRef = useRef<HTMLDivElement>(null)
  const width = useChartWidth(wrapRef)
  const [hover, setHover] = useState<number | null>(null)

  const n = days.length
  const showQty = enabled.qty
  const moneyKeys = SERIES_ORDER.filter((k) => k !== 'qty' && enabled[k])
  // Слева одна колонка под шкалу, две — когда показаны и рубли, и штуки.
  const padL = showQty ? (moneyKeys.length > 0 ? PAD_L_MONEY + PAD_L_QTY : PAD_L_QTY + 20) : PAD_L_MONEY

  // ── Рублёвая шкала ────────────────────────────────────────────────────────
  // Считается только по включённым сериям: выключенная не должна растягивать
  // ось и прижимать остальные линии к низу.
  let moneyMin = 0
  let moneyMax = 0
  for (const d of days) {
    for (const k of moneyKeys) {
      const v = SERIES[k].get(d)
      if (v < moneyMin) moneyMin = v
      if (v > moneyMax) moneyMax = v
    }
  }
  // Немного воздуха сверху: пик не должен упираться в край поля.
  const moneyScale = niceScale(moneyMin, moneyMax * 1.06, 5)
  const moneyAxes = chartScales({
    count: n,
    width,
    height: H,
    pad: { left: padL, right: PAD_R, top: PAD_T, bottom: PAD_B },
    minValue: moneyScale.min,
    maxValue: moneyScale.max,
    gridLines: moneyScale.ticks,
  })
  const { x, slot, innerW, innerH } = moneyAxes
  const yMoney = moneyAxes.y

  // ── Шкала штук ────────────────────────────────────────────────────────────
  // Второй осью, а не общим с деньгами: сорок штук и сорок тысяч рублей — разные
  // величины, и одна шкала на двоих сделала бы столбцы нечитаемыми.
  //
  // Считается от низа поля, а не от нулевой линии рублей: у денег бывают
  // отрицательные дни (убыток), и если привязывать штуки к той же базе, столбцы
  // приподнимались бы вместе с уходом оси в минус. Количество всегда начинается
  // от начала отсчёта — ноль штук у нижней границы.
  const qtyMax = showQty ? Math.max(...days.map((d) => d.soldQty), 0) : 0
  const qtyScale = niceScale(0, qtyMax * 1.06, 5)
  const yQty = (value: number) =>
    PAD_T + innerH * (1 - clamp(value, 0, qtyScale.max) / (qtyScale.max || 1))

  const barW = clamp(slot * 0.5, 1.5, 18)
  const labelStep = Math.max(1, Math.ceil(n / 10))

  const hovered = hover !== null ? days[hover] : null
  const hoverX = hover !== null ? x(hover) : 0
  // Тултип привязываем к верхней включённой точке дня, иначе он прыгал бы по
  // низу графика вслед за нулевой прибылью.
  const hoverY =
    hover === null
      ? 0
      : Math.min(
          ...moneyKeys.map((k) => yMoney(SERIES[k].get(days[hover]))),
          showQty ? yQty(days[hover].soldQty) : Number.POSITIVE_INFINITY,
        )

  const TIP_W = 248
  const TIP_H = 252
  const { left: tipLeft, top: tipTop } = tooltipPosition({
    hoverX,
    hoverY: Number.isFinite(hoverY) ? hoverY : PAD_T,
    width,
    height: H,
    tipWidth: TIP_W,
    tipHeight: TIP_H,
    offset: 16,
    padRight: PAD_R,
  })

  const onMove = (e: React.MouseEvent<SVGRectElement>) => {
    if (n === 0) return
    const rect = (e.currentTarget.ownerSVGElement as SVGSVGElement).getBoundingClientRect()
    const mx = e.clientX - rect.left
    setHover(clamp(Math.floor((mx - padL) / slot), 0, n - 1))
  }

  /** Общая база рисунка: от неё растут столбцы и в ней сходятся линии. */
  const zeroY = yMoney(0)

  /**
   * Цвет точки в шапке тултипа.
   *
   * <p>Раньше это был цвет выбранной метрики — единственной на графике. Теперь
   * показателей может быть несколько, и точка берётся у первого включённого:
   * она обозначает день, а не конкретную серию.
   */
  const headerKey = SERIES_ORDER.find((k) => enabled[k]) ?? 'income'

  return (
    <div ref={wrapRef} className="relative w-full select-none">
      <svg width={width} height={H} className="block">
        {/* сетка пунктиром: сплошные линии читаются как ещё одна серия данных, а
            это всего лишь разметка. Нулевая линия плотнее остальных — это база. */}
        {moneyScale.ticks.map((v, i) => (
          <g key={`grid-${i}`}>
            <line
              x1={padL}
              x2={width - PAD_R}
              y1={yMoney(v)}
              y2={yMoney(v)}
              stroke="#e2e8f0"
              strokeWidth={1}
              strokeDasharray="3 4"
            />
            {moneyKeys.length > 0 && (
              <text
                x={padL - 8}
                y={yMoney(v) + 4}
                textAnchor="end"
                fontSize={11}
                fontWeight={600}
                fill="#94a3b8"
                className="tnum"
              >
                {fmtAxis(v)}
              </text>
            )}
          </g>
        ))}

        {/* Шкала штук: подписи из своей сетки, а не из рублёвой.
            Две шкалы независимы, как в кабинетах маркетплейсов: если считать штуки
            по долям рублёвой сетки, получаются подписи 13 и 27 вместо 10 и 20 —
            круглые числа важнее того, чтобы линии сетки совпадали. */}
        {showQty &&
          qtyScale.ticks.map((q, i) => (
            <text
              key={`qty-${i}`}
              x={PAD_L_QTY - 8}
              y={yQty(q) + 4}
              textAnchor="end"
              fontSize={11}
              fontWeight={600}
              fill="#94a3b8"
              className="tnum"
            >
              {`${fmtNum(Math.round(q))} шт`}
            </text>
          ))}

        {/* Разделитель шкал: две оси не должны читаться как одна колонка чисел */}
        {showQty && moneyKeys.length > 0 && (
          <line
            x1={PAD_L_QTY - 2}
            x2={PAD_L_QTY - 2}
            y1={PAD_T - 4}
            y2={H - PAD_B + 4}
            stroke="#e2e8f0"
            strokeWidth={1}
          />
        )}

        {/* столбцы количества продаж: растут от начала отсчёта шкалы штук */}
        {showQty &&
          days.map((d, i) => {
            const base = yQty(0)
            const top = yQty(d.soldQty)
            const h = Math.max(0, base - top)
            if (h < 0.5) return null
            return (
              <motion.rect
                key={`bar-${d.date}`}
                initial={{ height: 0, y: base }}
                animate={{ height: h, y: top }}
                transition={{ type: 'spring', stiffness: 260, damping: 30 }}
                x={x(i) - barW / 2}
                width={barW}
                rx={Math.min(3, barW / 2)}
                fill={SERIES.qty.color}
                opacity={hover === null || hover === i ? 0.75 : 0.35}
                style={{ transition: 'opacity .15s' }}
              />
            )
          })}

        {/* маркеры незагруженных / уточняемых дней на базовой линии */}
        {days.map((d, i) =>
          d.state === 'ok' ? null : (
            <rect
              key={`st-${d.date}`}
              x={x(i) - Math.max(2, barW / 2)}
              y={zeroY}
              width={Math.max(4, barW)}
              height={4}
              rx={2}
              fill={d.state === 'failed' ? '#f43f5e' : d.state === 'missing' ? '#fbbf24' : '#38bdf8'}
            />
          ),
        )}

        {/* линии денег. Точек нет вовсе: на периоде в тридцать дней их тридцать на
            каждую линию, и они превращают график в пунктир из кружков, закрывая
            саму кривую. День читается по подсветке и тултипу. */}
        {moneyKeys.map((k) => (
          <g key={k}>
            {n > 1 && (
              <motion.path
                d={monotonePath(days.map((d, i) => ({ x: x(i), y: yMoney(SERIES[k].get(d)) })))}
                fill="none"
                stroke={SERIES[k].color}
                strokeWidth={2.5}
                strokeLinecap="round"
                strokeLinejoin="round"
                initial={{ pathLength: 0, opacity: 0 }}
                animate={{ pathLength: 1, opacity: 1 }}
                transition={{ duration: 0.8, ease: [0.33, 1, 0.68, 1] }}
              />
            )}
          </g>
        ))}

        {/* подсветка дня */}
        {hover !== null && (
          <rect
            x={hoverX - slot / 2}
            y={PAD_T - 6}
            width={slot}
            height={innerH + 6}
            fill="#0f172a"
            opacity={0.045}
            pointerEvents="none"
          />
        )}

        {/* подписи оси X */}
        {days.map((d, i) =>
          i % labelStep === 0 ? (
            <text
              key={`x-${d.date}`}
              x={x(i)}
              y={H - 10}
              textAnchor="middle"
              fontSize={11}
              fontWeight={600}
              fill="#94a3b8"
            >
              {fmtDayShort(d.date)}
            </text>
          ) : null,
        )}

        {/* зона наведения */}
        <rect
          x={padL - slot / 2}
          y={0}
          width={innerW + slot}
          height={H}
          fill="transparent"
          onMouseMove={onMove}
          onMouseLeave={() => setHover(null)}
        />
      </svg>

      {/* HTML-тултип с детализацией */}
      {hovered && (
        <div
          className="pointer-events-none absolute z-20 w-[248px] rounded-2xl border border-slate-200/80 bg-white/95 p-3.5 shadow-xl shadow-slate-900/10 backdrop-blur-md"
          style={{
            left: tipLeft,
            top: tipTop,
            transition: 'left .1s linear, top .1s linear',
          }}
        >
          <div className="flex items-center gap-2 pb-2">
            <span
              className="h-2.5 w-2.5 rounded-full"
              style={{ background: SERIES[headerKey].color }}
            />
            <span className="text-[12.5px] font-extrabold text-slate-900">
              {fmtDateLong(hovered.date)}
            </span>
          </div>
          {hovered.state !== 'ok' && (
            <div
              className={cn(
                'mb-1.5 rounded-lg px-2 py-1 text-[11px] font-semibold',
                hovered.state === 'failed'
                  ? 'bg-rose-50 text-rose-600'
                  : hovered.state === 'missing'
                    ? 'bg-amber-50 text-amber-700'
                    : 'bg-sky-50 text-sky-700',
              )}
            >
              {hovered.state === 'failed'
                ? 'Не удалось загрузить этот день'
                : hovered.state === 'missing'
                  ? 'Данные за этот день не загружены'
                  : 'Данные ещё могут уточниться'}
            </div>
          )}
          {/* Расходные строки передаются со знаком минус: в DayFinance они хранятся
              положительными, потому что так их удобнее складывать, а показать надо
              именно расход. Знак ставится здесь, а не флагом в TipRow. */}
          <TipRow label="Выручка" value={hovered.sales} />
          {hovered.returns !== 0 && <TipRow label="Возвраты" value={-hovered.returns} />}
          <TipRow label="Бонусы" value={hovered.partners} />
          <TipRow label="Продажи" value={hovered.income} bold />
          <div className="my-1.5 border-t border-slate-100" />
          <TipRow label="Комиссия" value={-hovered.commission} />
          <TipRow label="Логистика" value={-hovered.logistics} />
          <TipRow label="Прочие расходы" value={-hovered.other} />
          <TipRow label="Расходы" value={-hovered.expenses} bold />
          <div className="my-1.5 border-t border-slate-100" />
          {/* Прибыль приходит со своим знаком: убыток должен показываться убытком,
              а не положительной суммой красным цветом. */}
          <TipRow
            label="Прибыль"
            value={hovered.profit}
            bold
            color={hovered.profit >= 0 ? '#059669' : '#f43f5e'}
          />
          {(hovered.soldQty > 0 || hovered.returnedQty > 0) && (
            <div className="mt-1.5 flex justify-between border-t border-slate-100 pt-1.5 text-[11.5px] font-semibold text-slate-400">
              <span>Продано: {hovered.soldQty} шт</span>
              <span>Возвращено: {hovered.returnedQty} шт</span>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
