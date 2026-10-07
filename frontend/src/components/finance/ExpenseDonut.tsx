import { useEffect, useMemo, useState } from 'react'
import { motion } from 'framer-motion'
import type { FinanceTotals } from '@/types'
import { AnimatedNumber } from '@/components/ui'

/** Главные статьи расходов: большие сектора круга и три строки легенды. */
const MAIN_COLORS = {
  commission: '#4f80f0',
  logistics: '#9d7bf5',
  other: '#fb7ba9',
} as const

/**
 * Цвета подсекторов «прочих расходов».
 *
 * Разносятся по кругу и по списку справа, поэтому заданы здесь одним массивом.
 * Если бы каждый список брал свои цвета, они разошлись бы при первом же изменении
 * порядка, и заметить это можно было бы только глазами на скриншоте.
 *
 * Последний — серый: в нём «Другие», то есть то, что уже не названо отдельной
 * категорией. Серый читается как «не разбирали», а не как ещё одна статья.
 */
const SUBSECTOR_COLORS = ['#fb8c3c', '#f9c846', '#35cf8a', '#5aa9f8', '#2bb8c4']
const REST_COLOR = '#a8b0bd'

/** Сколько категорий показываем поимённо. Остальное сворачивается в «Другие». */
const MAX_ITEMS = 4

const R = 62
const C = 2 * Math.PI * R
const GAP = 2.5
/** Половина толщины сектора: рамка идёт по краям сектора, а не по его центру. */
const HALF = 12
/** Толщина розовой рамки, объединяющей все прочие расходы. */
const BORDER = 10

const CX = 84
const CY = 84

/** Точка на окружности: угол в радианах, отсчёт от верхней точки по часовой стрелке. */
function point(radius: number, angle: number) {
  return `${(CX + radius * Math.cos(angle)).toFixed(2)} ${(CY + radius * Math.sin(angle)).toFixed(2)}`
}

/**
 * Замкнутый контур рамки сектора: внешняя дуга, торец, внутренняя дуга, торец.
 *
 * <p>Рамка рисуется одним элементом, а не обводкой у каждого подсектора. Обводка
 * у каждого дала бы розовое кольцо вокруг каждой категории — и розовый перестал бы
 * означать «это прочие расходы», хотя именно это он должен означать. Здесь розовым
 * обведён весь блок целиком, а промежутки между категориями внутри остаются пустыми.
 *
 * <p>Внутренний радиус равен внешнему краю сектора, а не его середине: рамка идёт
 * по краю. Если взять радиус сектора, розовым залился бы весь блок и закрыл бы
 * категории, которые он должен обводить.
 *
 * <p>Контур повторяет контур сектора, поэтому содержит тот же зазор: иначе розовый
 * примыкал бы к соседнему сектору вплотную и сливался с ним в одно пятно.
 */
function ringSectorPath(outer: number, inner: number, from: number, to: number): string {
  const large = to - from > Math.PI ? 1 : 0
  const sweep = to > from ? 1 : 0
  return [
    `M ${point(outer, from)}`,
    `A ${outer} ${outer} 0 ${large} ${sweep} ${point(outer, to)}`,
    `L ${point(inner, to)}`,
    `A ${inner} ${inner} 0 ${large} ${sweep ? 0 : 1} ${point(inner, from)}`,
    'Z',
  ].join(' ')
}

/** Одна строка разбивки: категория прочих расходов и её доля. */
interface Slice {
  /** что показываем: описание из справочника, а при его отсутствии — служебное имя */
  label: string
  /** служебное название типа начисления: в подсказке, чтобы можно было найти его в OZON */
  serviceName: string
  value: number
  color: string
}

/**
 * Сектор круга.
 *
 * <p>`outlined` отличает подсектора прочих расходов: у них есть розовая обводка,
 * у комиссии и логистики — нет. Поле обязательное, а не необязательное: иначе
 * TypeScript не смог бы проверить обращение к нему и ошибка всплыла бы только при
 * сборке, после того как код уже написан.
 */
interface Sector {
  label: string
  color: string
  value: number
  outlined: boolean
}

/**
 * Разбивка «прочих расходов» на категории.
 *
 * Считается один раз и используется и для круга, и для списка справа. Два
 * независимых подсчёта разошлись бы при появлении нового типа начисления — и
 * разошлись бы молча, потому что обе цифры выглядели бы правдоподобно.
 */
function useOtherSlices(totals: FinanceTotals): Slice[] {
  return useMemo(() => {
    const typed = totals.otherByType.filter((t) => t.amount > 0)
    const top = typed.slice(0, MAX_ITEMS)
    const typedSum = typed.reduce((s, t) => s + t.amount, 0)
    const shownSum = top.reduce((s, t) => s + t.amount, 0)
    // Расходы без расшифровки: бэкенд не знает тип начисления, назвать их нечем.
    const unexplained = Math.max(0, totals.other - typedSum)
    const rest = typedSum - shownSum + unexplained

    const slices: Slice[] = top.map((t, i) => ({
      // Показывается описание из справочника маркетплейса: «Оплата за клик»
      // понятно продавцу, служебное имя PayPerClick — нет. Подпись без описания
      // остаётся служебной, иначе статья расходов стала бы безымянной.
      label: t.description?.trim() || t.name,
      serviceName: t.name,
      value: t.amount,
      color: SUBSECTOR_COLORS[i % SUBSECTOR_COLORS.length],
    }))
    if (rest > 0.5) {
      slices.push({ label: 'Другие', serviceName: 'Другие', value: rest, color: REST_COLOR })
    }
    return slices
  }, [totals])
}

export default function ExpenseDonut({ totals }: { totals: FinanceTotals }) {
  const [t, setT] = useState(0)
  const slices = useOtherSlices(totals)

  useEffect(() => {
    setT(0)
    const start = performance.now()
    let raf = 0
    const tick = (now: number) => {
      const p = Math.min(1, (now - start) / 900)
      setT(1 - Math.pow(1 - p, 3))
      if (p < 1) raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [totals])

  const total = Math.max(1, totals.expenses)

  // Секторы круга в порядке обхода: комиссия, логистика, затем подсектора прочих.
  const sectors = useMemo<Sector[]>(() => {
    const otherTotal = Math.max(1, totals.other)
    return [
      {
        label: 'Комиссия',
        color: MAIN_COLORS.commission,
        value: totals.commission,
        outlined: false,
      },
      {
        label: 'Логистика',
        color: MAIN_COLORS.logistics,
        value: totals.logistics,
        outlined: false,
      },
      ...slices.map((s) => ({
        label: s.label,
        color: s.color,
        value: (s.value / otherTotal) * totals.other,
        outlined: true,
      })),
    ]
  }, [totals, slices])

  // Смещения считаются заранее: подсектора и рамка должны начинаться в одной точке,
  // иначе розовый концентрический не совпадёт с блоком, который он обводит.
  const layout = useMemo(() => {
    let acc = 0
    return sectors.map((s) => {
      const share = s.value / total
      const start = acc
      acc += share
      return { ...s, share, start }
    })
  }, [sectors, total])

  // Доля и начало блока прочих расходов: рамка обводит их целиком.
  const group = useMemo(() => {
    const parts = layout.filter((s) => s.outlined)
    if (parts.length === 0) return null
    return {
      start: parts[0].start,
      share: parts.reduce((sum, p) => sum + p.share, 0),
    }
  }, [layout])

  /**
   * Контур розовой рамки для текущего момента анимации.
   *
   * <p>Углы считаются от верхней точки так же, как сектора, иначе рамка уехала бы
   * относительно блока, который обводит, и это выглядело бы как «рамка не на том
   * месте», а не как ошибка отрисовки.
   */
const groupOutline = (progress: number) => {
    if (!group) return ''
    // Углы рамки повторяют углы секторов, а не приблизительно равны им.
    //
    // У сектора длина равна share·C − GAP, а начало сдвинуто на GAP/2 вперёд по
    // ободу. Поэтому крайний сектор группы начинается на start·C − GAP/2 и
    // заканчивается на (start+share)·C − 3·GAP/2: зазор съедается с двух сторон,
    // и справа его вдвое больше, чем слева. Отсюда и разные поправки внизу.
    //
    // Поправки не умножаются на progress: зазор между секторами не растёт вместе с
    // анимацией. Умножение сдвигало рамку всё дальше по часовой стрелке, и на
    // середине анимации её края оказывались под другим углом, чем края соседних
    // секторов.
    const from = -Math.PI / 2 + group.start * 2 * Math.PI * progress - GAP / (2 * R)
    const to =
      -Math.PI / 2 + (group.start + group.share) * 2 * Math.PI * progress - (3 * GAP) / (2 * R)
    if (to - from < 0.001) return ''
    // По внешнему краю сектора, а не по его середине: иначе розовым залился бы
    // весь блок и закрыл бы категории, которые он должен обводить.
    return ringSectorPath(R + HALF + BORDER, R + HALF + 1, from, to)
  }

  const otherSum = slices.reduce((s, i) => s + i.value, 0)
  const otherShare = Math.max(1, otherSum)

  return (
    <motion.section
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, delay: 0.36, ease: [0.22, 1, 0.36, 1] }}
      className="rounded-2xl border border-slate-200/70 bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
    >
      <div className="grid gap-6 lg:grid-cols-2 lg:gap-8">
        {/* ── Слева: круг и три главные статьи ───────────────────────────────── */}
        <div className="min-w-0">
          <h2 className="text-[17px] font-extrabold tracking-tight text-slate-900">
            Структура расходов
          </h2>

          <div className="mt-4 flex flex-wrap items-center gap-6 sm:flex-nowrap sm:gap-4 lg:gap-6">
            <div className="relative mx-auto shrink-0 sm:mx-0">
              <svg width="168" height="168" viewBox="0 0 168 168">
                <circle cx={CX} cy={CY} r={R} fill="none" stroke="#f1f5f9" strokeWidth="24" />

                {layout.map((s) => (
                  <circle
                    key={s.label}
                    cx={CX} cy={CY} r={R}
                    fill="none"
                    stroke={s.color}
                    strokeWidth="24"
                    strokeDasharray={`${Math.max(0, s.share * C * t - GAP)} ${C}`}
                    strokeDashoffset={-s.start * C * t + GAP / 2}
                    transform={`rotate(-90 ${CX} ${CY})`}
                    strokeLinecap="butt"
                  />
                ))}

                {/* Рамка вокруг всех прочих расходов — одним элементом поверх
                    подсекторов, поэтому она читается как обводка блока, а не как
                    ещё один сектор. */}
                {slices.length > 0 && (
                  <path
                    d={groupOutline(t)}
                    fill={MAIN_COLORS.other}
                    strokeLinejoin="round"
                  />
                )}
              </svg>
              <div className="absolute inset-0 flex flex-col items-center justify-center">
                <span className="tnum text-[20px] font-extrabold tracking-tight text-slate-900">
                  <AnimatedNumber value={totals.expenses} />
                </span>
                <span className="mt-0.5 text-[10.5px] font-semibold text-slate-400">
                  Всего расходов
                </span>
              </div>
            </div>

            <div className="min-w-[200px] flex-1 space-y-3.5">
              <LegendRow
                label="Комиссия"
                color={MAIN_COLORS.commission}
                value={totals.commission}
                share={totals.commission / total}
                delay={0.45}
              />
              <LegendRow
                label="Логистика"
                color={MAIN_COLORS.logistics}
                value={totals.logistics}
                share={totals.logistics / total}
                delay={0.55}
              />
              <LegendRow
                label="Прочие расходы"
                color={MAIN_COLORS.other}
                value={totals.other}
                share={totals.other / total}
                delay={0.65}
              />
            </div>
          </div>
        </div>

        {/* ── Справа: разбивка прочих расходов ───────────────────────────────── */}
        <div className="min-w-0 lg:border-l lg:border-slate-200/70 lg:pl-8">
          <div className="flex items-baseline justify-between gap-4">
            <h2 className="text-[17px] font-extrabold tracking-tight text-slate-900">
              Прочие расходы
            </h2>
            
          </div>

          {slices.length === 0 ? (
            <p className="mt-6 text-[13px] font-medium text-slate-400">
              За выбранный период прочих расходов нет.
            </p>
          ) : (
            <div className="mt-5 space-y-4">
              {slices.map((it, i) => {
                const pct = Math.round((it.value / otherShare) * 100)
                return (
                  <motion.div
                    key={it.label}
                    initial={{ opacity: 0, x: 10 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: 0.5 + i * 0.09 }}
                    className="flex items-center gap-4"
                  >
                    <span
                      className="h-3 w-3 shrink-0 rounded-full"
                      style={{ background: it.color }}
                    />
                    <span
                      className="w-[150px] shrink-0 truncate text-[13px] font-semibold text-slate-600"
                      title={it.label === it.serviceName ? it.label : `${it.label} (${it.serviceName})`}
                    >
                      {it.label}
                    </span>
                    <div className="h-2 min-w-[60px] flex-1 overflow-hidden rounded-full bg-slate-100">
                      <motion.div
                        className="h-full rounded-full"
                        style={{ background: it.color }}
                        initial={{ width: 0 }}
                        animate={{ width: `${pct}%` }}
                        transition={{
                          duration: 0.8,
                          delay: 0.5 + i * 0.09,
                          ease: [0.22, 1, 0.36, 1],
                        }}
                      />
                    </div>
                    <span className="tnum w-[92px] shrink-0 text-right text-[13px] font-extrabold text-slate-900">
                      <AnimatedNumber value={it.value} />
                    </span>
                    <span className="tnum w-9 shrink-0 text-right text-[12px] font-semibold text-slate-400">
                      {pct}%
                    </span>
                  </motion.div>
                )
              })}
            </div>
          )}
        </div>
      </div>
    </motion.section>
  )
}

/** Строка легенды главных статей: точка, название, сумма и доля от всех расходов. */
function LegendRow({
  label,
  color,
  value,
  share,
  delay,
}: {
  label: string
  color: string
  value: number
  share: number
  delay: number
}) {
  return (
    <motion.div
      initial={{ opacity: 0, x: 10 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay }}
      className="flex items-center gap-3"
    >
      <span className="h-3 w-3 shrink-0 rounded-full" style={{ background: color }} />
      <span className="text-[13px] font-semibold text-slate-600">{label}</span>
      <span className="tnum ml-auto text-[13px] font-extrabold text-slate-900">
        <AnimatedNumber value={value} />
      </span>
      <span className="tnum w-9 text-right text-[12px] font-semibold text-slate-400">
        {Math.round(share * 100)}%
      </span>
    </motion.div>
  )
}
