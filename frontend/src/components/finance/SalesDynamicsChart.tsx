import { useLayoutEffect, useMemo, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import type { DayState } from '@/types'
import { clamp, fmtDateLong, fmtDayShort, niceMax } from '@/utils/format'
import { cn } from '@/utils/cn'

/** День динамики с уже проставленным состоянием: без него график врёт о пропусках. */
interface Point {
  date: string
  state: DayState
  sold: number
  returned: number
}

const H = 260
const PAD_L = 46
const PAD_R = 14
const PAD_T = 16
const PAD_B = 30

const SOLD_COLOR = '#4f80f0'
const RETURNED_COLOR = '#f43f5e'

/**
 * График продаж: сколько единиц продано и возвращено по дням.
 *
 * <p>Намеренно отдельный компонент, а не режим графика на «Обзоре»: там линии денег,
 * здесь количество в штуках, и общая подпись оси с рублями сбивала бы с толку. Общее
 * у них только устройство — вёрстка и наведение, но смешивать их в одном компоненте
 * значило бы тащить в товары весь денежный разбор.
 */
export default function SalesDynamicsChart({
  points,
  filterLabel,
  loading,
}: {
  points: Point[]
  filterLabel: string
  loading?: boolean
}) {
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

  const n = points.length
  const innerW = Math.max(10, width - PAD_L - PAD_R)
  const innerH = H - PAD_T - PAD_B
  const slot = innerW / Math.max(1, n)

  // Возвраты рисуются отдельными полосками внутри дня, поэтому они в масштабе не
  // участвуют: иначе один возврат на 500 единиц растянул бы шкалу так, что продажи
  // стали бы неразличимы.
  const maxSold = n ? Math.max(...points.map((p) => p.sold)) : 0
  const yMax = maxSold > 0 ? niceMax(maxSold * 1.08) : 10
  const barW = clamp(slot * 0.6, 3, 22)

  const x = (i: number) => PAD_L + slot * (i + 0.5)
  const y = (v: number) => PAD_T + innerH * (1 - clamp(v, 0, yMax) / yMax)

  const gridLines = useMemo(() => [0, 0.25, 0.5, 0.75, 1].map((f) => f * yMax), [yMax])
  const labelStep = Math.max(1, Math.ceil(n / 10))

  const hovered = hover !== null ? points[hover] : null
  const TIP_W = 190
  const TIP_H = 116
  const hoverX = hover !== null ? x(hover) : 0
  const hoverY = hover !== null ? y(points[hover].sold) : 0
  const tipLeft = hoverX + 14 + TIP_W < width - PAD_R
    ? Math.min(hoverX + 14, width - TIP_W - 4)
    : Math.max(hoverX - 14 - TIP_W, 4)
  const tipTop = clamp(hoverY - TIP_H / 2, 4, H - TIP_H - 4)

  const onMove = (e: React.MouseEvent<SVGRectElement>) => {
    if (n === 0) return
    const rect = (e.currentTarget.ownerSVGElement as SVGSVGElement).getBoundingClientRect()
    const mx = e.clientX - rect.left
    setHover(clamp(Math.floor((mx - PAD_L) / slot), 0, n - 1))
  }

  if (n === 0) {
    return (
      <div className="flex h-[220px] items-center justify-center text-[13px] font-medium text-slate-400">
        Нет данных за выбранный период.
      </div>
    )
  }

  return (
    <div className={cn('relative w-full select-none', loading && 'opacity-60 transition-opacity')}>
      <svg width={width} height={H} className="block">
        {gridLines.map((v, i) => (
          <g key={i}>
            <line
              x1={PAD_L} x2={width - PAD_R} y1={y(v)} y2={y(v)}
              stroke={i === 0 ? '#e2e8f0' : '#eef2f8'} strokeWidth={1}
            />
            <text
              x={PAD_L - 8} y={y(v) + 4} textAnchor="end"
              fontSize={11} fontWeight={600} fill="#94a3b8" className="tnum"
            >
              {Math.round(v)}
            </text>
          </g>
        ))}

        {points.map((p, i) => {
          const barTop = y(p.sold)
          const barH = Math.max(2, H - PAD_B - barTop)
          // Возвраты — тонкая полоска у основания дня, а не второй столбец:
          // второй столбец читался бы как отдельная метрика того же масштаба.
          const retH = p.returned > 0 ? clamp(3, 2, barH) : 0
          return (
            <g key={p.date}>
              <motion.rect
                initial={{ height: 0, y: H - PAD_B }}
                animate={{ height: barH, y: barTop }}
                transition={{ type: 'spring', stiffness: 260, damping: 30 }}
                x={x(i) - barW / 2}
                width={barW}
                rx={Math.min(4, barW / 2)}
                fill={p.state === 'missing' || p.state === 'failed' ? '#e2e8f0' : SOLD_COLOR}
                opacity={hover === null || hover === i ? 0.9 : 0.5}
                style={{ transition: 'opacity .15s' }}
              />
              {retH > 0 && (
                <rect
                  x={x(i) - barW / 2}
                  y={H - PAD_B - retH}
                  width={barW}
                  height={retH}
                  rx={1}
                  fill={RETURNED_COLOR}
                  opacity={hover === null || hover === i ? 1 : 0.55}
                  style={{ transition: 'opacity .15s' }}
                />
              )}
            </g>
          )
        })}

        {points.map((p, i) =>
          p.state === 'ok' ? null : (
            <rect
              key={`st-${p.date}`}
              x={x(i) - Math.max(2, barW / 2)}
              y={H - PAD_B}
              width={Math.max(4, barW)}
              height={4}
              rx={2}
              fill={p.state === 'failed' ? RETURNED_COLOR : p.state === 'missing' ? '#fbbf24' : '#38bdf8'}
            />
          ),
        )}

        {hover !== null && (
          <rect
            x={hoverX - slot / 2} y={PAD_T - 6} width={slot} height={innerH + 6}
            fill="#0f172a" opacity={0.045} pointerEvents="none"
          />
        )}

        {points.map((p, i) =>
          i % labelStep === 0 ? (
            <text
              key={p.date} x={x(i)} y={H - 9} textAnchor="middle"
              fontSize={11} fontWeight={600} fill="#94a3b8"
            >
              {fmtDayShort(p.date)}
            </text>
          ) : null,
        )}

        <rect
          x={PAD_L - slot / 2} y={0} width={innerW + slot} height={H}
          fill="transparent" onMouseMove={onMove} onMouseLeave={() => setHover(null)}
        />
      </svg>

      {hovered && (
        <div
          className="pointer-events-none absolute z-20 w-[190px] rounded-2xl border border-slate-200/80 bg-white/95 p-3 shadow-xl shadow-slate-900/10 backdrop-blur-md"
          style={{ left: tipLeft, top: tipTop, transition: 'left .1s linear, top .1s linear' }}
        >
          <div className="flex items-center gap-2 pb-1.5">
            <span className="h-2.5 w-2.5 rounded-full" style={{ background: SOLD_COLOR }} />
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
          <TipLine label="Продано" value={hovered.sold} color={SOLD_COLOR} />
          <TipLine label="Возвращено" value={hovered.returned} color={RETURNED_COLOR} />
        </div>
      )}

      <div className="mt-2 flex items-center gap-4 px-1 text-[11.5px] font-semibold text-slate-400">
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-sm" style={{ background: SOLD_COLOR }} />
          Продано, шт
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-sm" style={{ background: RETURNED_COLOR }} />
          Возвращено, шт
        </span>
        <span className="ml-auto truncate text-slate-400">{filterLabel}</span>
      </div>
    </div>
  )
}

/** Строка тултипа: подпись, число единиц и цветная точка значения. */
function TipLine({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className="flex items-center justify-between gap-4 py-[2.5px]">
      <span className="text-[12px] font-medium text-slate-500">{label}</span>
      <span className="flex items-center gap-1.5">
        <span className="tnum text-[12px] font-extrabold" style={{ color }}>
          {value}
        </span>
        <span className="text-[11px] font-medium text-slate-400">шт</span>
      </span>
    </div>
  )
}
