import { useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { BarChart3, Table2 } from 'lucide-react'
import { cn } from '../utils/cn'
import type { DayFinance } from '../types'
import FinanceChart, { METRIC_CONFIG, type MetricKey } from './FinanceChart'
import FinanceTable from './FinanceTable'

type View = 'chart' | 'table'

const METRIC_ORDER: MetricKey[] = ['income', 'expenses', 'profit']

export default function FinanceSection({ days }: { days: DayFinance[] }) {
  const [metric, setMetric] = useState<MetricKey>('income')
  const [view, setView] = useState<View>('chart')

  return (
    <motion.section
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, delay: 0.28, ease: [0.22, 1, 0.36, 1] }}
      className="rounded-2xl border border-slate-200/70 bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
    >
      {/* Шапка секции */}
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2.5">
        <h2 className="text-[17px] font-extrabold tracking-tight text-slate-900">
          Динамика финансов
        </h2>

        {/* переключатель метрики (только для графика) */}
        <AnimatePresence initial={false}>
          {view === 'chart' && (
            <motion.div
              initial={{ opacity: 0, width: 0 }}
              animate={{ opacity: 1, width: 'auto' }}
              exit={{ opacity: 0, width: 0 }}
              transition={{ duration: 0.2 }}
              className="order-last w-full overflow-hidden sm:order-none sm:w-auto"
            >
              <div className="flex w-fit rounded-full bg-slate-100 p-1">
                {METRIC_ORDER.map((m) => {
                  const cfg = METRIC_CONFIG[m]
                  const active = metric === m
                  return (
                    <button
                      key={m}
                      onClick={() => setMetric(m)}
                      className="relative rounded-full px-4 py-1.5 text-[12.5px] font-bold"
                    >
                      {active && (
                        <motion.span
                          layoutId="metric-pill"
                          className="absolute inset-0 rounded-full bg-white shadow-sm ring-1 ring-slate-200/70"
                          transition={{ type: 'spring', stiffness: 420, damping: 34 }}
                        />
                      )}
                      <span
                        className="relative z-10 flex items-center gap-1.5 transition-colors"
                        style={{ color: active ? cfg.color : '#64748b' }}
                      >
                        <span
                          className="h-1.5 w-1.5 rounded-full transition-opacity"
                          style={{ background: cfg.color, opacity: active ? 1 : 0.35 }}
                        />
                        {cfg.label}
                      </span>
                    </button>
                  )
                })}
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* переключатель «График / Таблица» */}
        <div className="ml-auto flex rounded-full bg-slate-100 p-1">
          {(
            [
              { id: 'chart', label: 'График', icon: BarChart3 },
              { id: 'table', label: 'Таблица', icon: Table2 },
            ] as const
          ).map(({ id, label, icon: Icon }) => {
            const active = view === id
            return (
              <button
                key={id}
                onClick={() => setView(id)}
                className="relative flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-[12px] font-bold"
              >
                {active && (
                  <motion.span
                    layoutId="view-pill"
                    className="absolute inset-0 rounded-full bg-brand-600 shadow-md shadow-brand-600/25"
                    transition={{ type: 'spring', stiffness: 420, damping: 34 }}
                  />
                )}
                <Icon size={14} className={cn('relative z-10', active ? 'text-white' : 'text-slate-400')} />
                <span className={cn('relative z-10 transition-colors', active ? 'text-white' : 'text-slate-500')}>
                  {label}
                </span>
              </button>
            )
          })}
        </div>
      </div>

      {/* Контент */}
      <div className="mt-4">
        <AnimatePresence mode="wait" initial={false}>
          {view === 'chart' ? (
            <motion.div
              key="chart"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.22 }}
            >
              <FinanceChart days={days} metric={metric} />
            </motion.div>
          ) : (
            <motion.div
              key="table"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.22 }}
            >
              <FinanceTable days={days} />
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </motion.section>
  )
}
