import { useState } from 'react'
import { motion } from 'framer-motion'
import { Table2 } from 'lucide-react'
import type { DayFinance } from '@/types'
import FinanceChart, { METRIC_CONFIG, type MetricKey } from '@/components/finance/FinanceChart'
import DailyFinanceTable from '@/components/finance/DailyFinanceTable'
import Modal from '@/components/ui/Modal'
import { SecondaryButton } from '@/components/ui/controls'

const METRIC_ORDER: MetricKey[] = ['income', 'expenses', 'profit']

/**
 * Динамика финансов за период.
 *
 * <p>График и таблица — не два равноправных режима одного блока, а два разных
 * способа смотреть: по графику видно форму периода, по таблице — конкретные числа с
 * расшифровкой расходов. Поэтому таблица открывается отдельным окном почти на весь
 * экран, а не подменяет график внутри карточки: в карточке шириной в треть экрана
 * одиннадцать колонок не помещаются, и раньше их приходилось прокручивать внутри
 * маленькой рамки.
 */
export default function FinanceSection({ days }: { days: DayFinance[] }) {
  const [metric, setMetric] = useState<MetricKey>('income')
  const [tableOpen, setTableOpen] = useState(false)

  return (
    <>
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

          <div className="order-last w-full sm:order-none sm:w-auto">
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
          </div>

          <div className="ml-auto">
            <SecondaryButton
              onClick={() => setTableOpen(true)}
              disabled={days.length === 0}
              title="Открыть таблицу по дням с расшифровкой расходов"
            >
              <Table2 size={14} />
              Показать таблицу
            </SecondaryButton>
          </div>
        </div>

        {/* Контент */}
        <div className="mt-4">
          <FinanceChart days={days} metric={metric} />
        </div>
      </motion.section>

      <Modal
        open={tableOpen}
        title="Аналитика по дням"
        onClose={() => setTableOpen(false)}
        size="full"
      >
        <DailyFinanceTable days={days} />
      </Modal>
    </>
  )
}
