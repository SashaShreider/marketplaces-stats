import { motion } from 'framer-motion'
import type { FinanceTotals } from '../types'
import { AnimatedNumber } from './ui'

const BAR_COLORS = ['#3b6de8', '#5f8ff3', '#8fb6f8', '#bdd4fb', '#d4e3fc']
const MAX_ITEMS = 4

export default function OtherExpenses({ totals }: { totals: FinanceTotals }) {
  // типы начислений приходят из справочника маркетплейса — берём крупнейшие,
  // остальное и расходы без расшифровки сворачиваем в «Другие»
  const typed = totals.otherByType.filter((t) => t.amount > 0)
  const top = typed.slice(0, MAX_ITEMS)
  const typedSum = typed.reduce((s, t) => s + t.amount, 0)
  const shownSum = top.reduce((s, t) => s + t.amount, 0)
  const unexplained = Math.max(0, totals.other - typedSum)
  const restAmount = typedSum - shownSum + unexplained

  const items = [...top.map((t) => ({ label: t.name, value: t.amount }))]
  if (restAmount > 0.5) items.push({ label: 'Другие', value: restAmount })

  const base = Math.max(1, items.reduce((s, i) => s + i.value, 0))

  return (
    <motion.section
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, delay: 0.42, ease: [0.22, 1, 0.36, 1] }}
      className="rounded-2xl border border-slate-200/70 bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
    >
      <h2 className="text-[17px] font-extrabold tracking-tight text-slate-900">Прочие расходы</h2>

      {items.length === 0 ? (
        <p className="mt-6 text-[13px] font-medium text-slate-400">
          За выбранный период прочих расходов нет.
        </p>
      ) : (
        <div className="mt-4 space-y-4">
          {items.map((it, i) => {
            const pct = Math.round((it.value / base) * 100)
            return (
              <div key={it.label} className="flex items-center gap-4">
                <span
                  className="w-[104px] shrink-0 truncate text-[13px] font-semibold text-slate-600"
                  title={it.label}
                >
                  {it.label}
                </span>
                <div className="h-2 min-w-[60px] flex-1 overflow-hidden rounded-full bg-slate-100">
                  <motion.div
                    className="h-full rounded-full"
                    style={{ background: BAR_COLORS[i % BAR_COLORS.length] }}
                    initial={{ width: 0 }}
                    animate={{ width: `${pct}%` }}
                    transition={{ duration: 0.8, delay: 0.5 + i * 0.09, ease: [0.22, 1, 0.36, 1] }}
                  />
                </div>
                <span className="tnum w-[92px] shrink-0 text-right text-[13px] font-extrabold text-slate-900">
                  <AnimatedNumber value={it.value} />
                </span>
                <span className="tnum w-9 shrink-0 text-right text-[12px] font-semibold text-slate-400">
                  {pct}%
                </span>
              </div>
            )
          })}
        </div>
      )}
    </motion.section>
  )
}
