import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import type { FinanceTotals } from '../types'
import { AnimatedNumber } from './ui'

const SEGMENTS = [
  { label: 'Комиссия', color: '#4f80f0', get: (t: FinanceTotals) => t.commission },
  { label: 'Логистика', color: '#9d7bf5', get: (t: FinanceTotals) => t.logistics },
  { label: 'Прочие расходы', color: '#fb7ba9', get: (t: FinanceTotals) => t.other },
]

const R = 62
const C = 2 * Math.PI * R
const GAP = 2.5

export default function ExpenseDonut({ totals }: { totals: FinanceTotals }) {
  const [t, setT] = useState(0)

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
  let acc = 0

  return (
    <motion.section
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, delay: 0.36, ease: [0.22, 1, 0.36, 1] }}
      className="rounded-2xl border border-slate-200/70 bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
    >
      <h2 className="text-[17px] font-extrabold tracking-tight text-slate-900">Структура расходов</h2>

      <div className="mt-4 flex flex-wrap items-center gap-6 sm:flex-nowrap sm:gap-4 lg:gap-6">
        {/* Донат */}
        <div className="relative mx-auto shrink-0 sm:mx-0">
          <svg width="168" height="168" viewBox="0 0 168 168">
            <circle cx="84" cy="84" r={R} fill="none" stroke="#f1f5f9" strokeWidth="24" />
            {SEGMENTS.map((s) => {
              const share = s.get(totals) / total
              const len = Math.max(0, share * C * t - GAP)
              const offset = -acc * C * t
              acc += share
              return (
                <circle
                  key={s.label}
                  cx="84" cy="84" r={R}
                  fill="none"
                  stroke={s.color}
                  strokeWidth="24"
                  strokeDasharray={`${len} ${C}`}
                  strokeDashoffset={offset + GAP / 2}
                  transform="rotate(-90 84 84)"
                  strokeLinecap="butt"
                />
              )
            })}
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <span className="tnum text-[20px] font-extrabold tracking-tight text-slate-900">
              <AnimatedNumber value={totals.expenses} />
            </span>
            <span className="mt-0.5 text-[10.5px] font-semibold text-slate-400">Всего расходов</span>
          </div>
        </div>

        {/* Легенда */}
        <div className="min-w-[200px] flex-1 space-y-3.5">
          {SEGMENTS.map((s, i) => {
            const v = s.get(totals)
            const pct = Math.round((v / total) * 100)
            return (
              <motion.div
                key={s.label}
                initial={{ opacity: 0, x: 10 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.45 + i * 0.1 }}
                className="flex items-center gap-3"
              >
                <span className="h-3 w-3 shrink-0 rounded-full" style={{ background: s.color }} />
                <span className="text-[13px] font-semibold text-slate-600">{s.label}</span>
                <span className="tnum ml-auto text-[13px] font-extrabold text-slate-900">
                  <AnimatedNumber value={v} />
                </span>
                <span className="tnum w-9 text-right text-[12px] font-semibold text-slate-400">{pct}%</span>
              </motion.div>
            )
          })}
        </div>
      </div>
    </motion.section>
  )
}
