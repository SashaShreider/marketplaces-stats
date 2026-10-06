import { useLayoutEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import type { DayFinance } from '../types'
import { clamp, fmtAxis, fmtDateLong, fmtDayShort, fmtMoney, niceMax } from '../utils/format'

export type MetricKey = 'income' | 'expenses' | 'profit'

export const METRIC_CONFIG: Record<
  MetricKey,
  { label: string; color: string; get: (d: DayFinance) => number }
> = {
  income: { label: 'Доходы', color: '#3b6de8', get: (d) => d.income },
  expenses: { label: 'Расходы', color: '#f43f5e', get: (d) => d.expenses },
  profit: { label: 'Прибыль', color: '#10b981', get: (d) => d.profit },
}

const H = 340
const PAD_L = 66
const PAD_R = 14
const PAD_T = 20
const PAD_B = 34

function smoothPath(pts: { x: number; y: number }[]): string {
  if (pts.length < 2) return pts.length ? `M ${pts[0].x} ${pts[0].y}` : ''
  let d = `M ${pts[0].x} ${pts[0].y}`
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] ?? pts[i]
    const p1 = pts[i]
    const p2 = pts[i + 1]
    const p3 = pts[i + 2] ?? p2
    const c1x = p1.x + (p2.x - p0.x) / 6
    const c1y = p1.y + (p2.y - p0.y) / 6
    const c2x = p2.x - (p3.x - p1.x) / 6
    const c2y = p2.y - (p3.y - p1.y) / 6
    d += ` C ${c1x} ${c1y}, ${c2x} ${c2y}, ${p2.x} ${p2.y}`
  }
  return d
}

/**
 * Строка детализации в тултипе.
 *
 * Знак берётся из самого числа, а не из отдельного флага. Флаг означал бы, что
 * правильность показания зависит от того, не забыл ли его поставить вызывающий:
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

export default function FinanceChart({ days, metric }: { days: DayFinance[]; metric: MetricKey }) {
  const wrapRef = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(760)
  const [hover, setHover] = useState<number | null>(null)

  useLayoutEffect(() => {
    const el = wrapRef.current
    if (!el) return
    const ro = new ResizeObserver((entries) => {
      const w = entries[0].contentRect.width
      if (w > 0) setWidth(w)
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const n = days.length
  const innerW = Math.max(10, width - PAD_L - PAD_R)
  const innerH = H - PAD_T - PAD_B
  const slot = innerW / Math.max(1, n)
  const barW = clamp(slot * 0.56, 2, 20)

  const maxIncome = n ? Math.max(...days.map((d) => d.income)) : 0
  const yMax = maxIncome > 0 ? niceMax(maxIncome * 1.08) : 100000
  const y = (v: number) => PAD_T + innerH * (1 - clamp(v, 0, yMax) / yMax)
  const x = (i: number) => PAD_L + slot * (i + 0.5)

  const cfg = METRIC_CONFIG[metric]
  const pts = days.map((d, i) => ({ x: x(i), y: y(cfg.get(d)) }))
  const path = smoothPath(pts)

  const labelStep = Math.max(1, Math.ceil(n / 10))
  const gridLines = [0, 0.25, 0.5, 0.75, 1].map((f) => f * yMax)

  const hovered = hover !== null ? days[hover] : null
  const hoverX = hover !== null ? x(hover) : 0
  const hoverY = hover !== null ? pts[hover].y : 0
  // тултип сбоку от точки (не перекрывая её) и в пределах графика
  const TIP_W = 248
  const TIP_H = 252
  const tipOnRight = hoverX + 16 + TIP_W < width - PAD_R
  const tipLeft = tipOnRight
    ? Math.min(hoverX + 16, width - TIP_W - 4)
    : Math.max(hoverX - 16 - TIP_W, 4)
  const tipTop = clamp(hoverY - TIP_H / 2, 4, H - TIP_H - 4)

  const onMove = (e: React.MouseEvent<SVGRectElement>) => {
    const rect = (e.currentTarget.ownerSVGElement as SVGSVGElement).getBoundingClientRect()
    const mx = e.clientX - rect.left
    setHover(clamp(Math.round((mx - PAD_L) / slot - 0.5), 0, n - 1))
  }

  return (
    <div ref={wrapRef} className="relative w-full select-none">
      <svg width={width} height={H} className="block">
        <defs>
          <linearGradient id="barGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#c3d7fb" />
            <stop offset="100%" stopColor="#e3edfd" />
          </linearGradient>
        </defs>

        {/* сетка */}
        {gridLines.map((v, i) => (
          <g key={i}>
            <line
              x1={PAD_L} x2={width - PAD_R}
              y1={y(v)} y2={y(v)}
              stroke={i === 0 ? '#e2e8f0' : '#eef2f8'}
              strokeWidth={1}
            />
            <text
              x={PAD_L - 10} y={y(v) + 4}
              textAnchor="end" fontSize={11} fontWeight={600}
              fill="#94a3b8" className="tnum"
            >
              {fmtAxis(v)}
            </text>
          </g>
        ))}

        {/* столбцы = доходы (всегда) */}
        {days.map((d, i) => {
          const bTop = y(d.income)
          const bH = Math.max(2, H - PAD_B - bTop)
          return (
            <motion.rect
              key={d.date}
              initial={{ height: 0, y: H - PAD_B }}
              animate={{ height: bH, y: bTop }}
              transition={{ type: 'spring', stiffness: 260, damping: 30 }}
              x={x(i) - barW / 2}
              width={barW}
              rx={Math.min(4, barW / 2)}
              fill={metric === 'income' ? '#b9d1fa' : 'url(#barGrad)'}
              opacity={hover === null || hover === i ? 1 : 0.55}
              style={{ transition: 'opacity .15s' }}
            />
          )
        })}

        {/* маркеры незагруженных / уточняемых дней на оси */}
        {days.map((d, i) =>
          d.state === 'ok' ? null : (
            <rect
              key={`st-${d.date}`}
              x={x(i) - Math.max(2, barW / 2)}
              y={H - PAD_B}
              width={Math.max(4, barW)}
              height={4}
              rx={2}
              fill={d.state === 'failed' ? '#f43f5e' : d.state === 'missing' ? '#fbbf24' : '#38bdf8'}
            />
          ),
        )}

        {/* линия выбранной метрики */}
        {n > 1 && (
          <motion.path
            key={metric}
            d={path}
            fill="none"
            stroke={cfg.color}
            strokeWidth={2.5}
            strokeLinecap="round"
            initial={{ pathLength: 0, opacity: 0 }}
            animate={{ pathLength: 1, opacity: 1 }}
            transition={{ duration: 0.9, ease: [0.33, 1, 0.68, 1] }}
          />
        )}

        {/* точки */}
        {n <= 90 &&
          pts.map((p, i) => (
            <motion.circle
              key={`${metric}-${days[i].date}`}
              cx={p.x}
              cy={p.y}
              r={hover === i ? 6 : 4}
              fill="white"
              stroke={cfg.color}
              strokeWidth={2.5}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.25 + i * 0.01, duration: 0.25 }}
              style={{
                cursor: 'pointer',
                filter: hover === i ? `drop-shadow(0 2px 6px ${cfg.color}66)` : undefined,
                transition: 'r .15s ease',
              }}
            />
          ))}

        {/* вертикальная подсветка дня */}
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
              key={d.date}
              x={x(i)} y={H - 10}
              textAnchor="middle" fontSize={11} fontWeight={600}
              fill="#94a3b8"
            >
              {fmtDayShort(d.date)}
            </text>
          ) : null,
        )}

        {/* зона наведения */}
        <rect
          x={PAD_L - slot / 2} y={0}
          width={innerW + slot} height={H}
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
            <span className="h-2.5 w-2.5 rounded-full" style={{ background: cfg.color }} />
            <span className="text-[12.5px] font-extrabold text-slate-900">
              {fmtDateLong(hovered.date)}
            </span>
            <span className="ml-auto rounded-md bg-slate-100 px-1.5 py-0.5 text-[10px] font-bold text-slate-500">
              {cfg.label}
            </span>
          </div>
          {hovered.state !== 'ok' && (
            <div
              className={`mb-1.5 rounded-lg px-2 py-1 text-[11px] font-semibold ${
                hovered.state === 'failed'
                  ? 'bg-rose-50 text-rose-600'
                  : hovered.state === 'missing'
                    ? 'bg-amber-50 text-amber-700'
                    : 'bg-sky-50 text-sky-700'
              }`}
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
          <TipRow label="Продажи" value={hovered.sales} />
          {hovered.returns !== 0 && <TipRow label="Возвраты" value={-hovered.returns} />}
          <TipRow label="Партнёры" value={hovered.partners} />
          <TipRow label="Доходы" value={hovered.income} bold />
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
