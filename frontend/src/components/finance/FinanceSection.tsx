import { useState } from 'react'
import { motion } from 'framer-motion'
import { Table2 } from 'lucide-react'
import type { DayFinance } from '@/types'
import FinanceChart, { SERIES, SERIES_ORDER, type SeriesKey } from '@/components/finance/FinanceChart'
import DailyFinanceTable from '@/components/finance/DailyFinanceTable'
import Modal from '@/components/ui/Modal'
import { SecondaryButton } from '@/components/ui/controls'
import { cn } from '@/utils/cn'

/** Что включено при открытии: все три линии денег и столбцы количества. */
const INITIAL: Record<SeriesKey, boolean> = {
  income: true,
  expenses: true,
  profit: true,
  qty: true,
}

/**
 * Динамика финансов за период.
 *
 * <p>Показатели включаются независимо, а не переключаются по одному: на одном
 * графике видно и продажи, и расходы с прибылью, и связь между ними очевидна —
 * тогда как режим «одна линия за раз» заставлял держать разницу в голове.
 *
 * <p>График и таблица — два разных способа смотреть: по графику видно форму
 * периода, по таблице — конкретные числа с расшифровкой расходов. Поэтому таблица
 * открывается отдельным окном почти на весь экран, а не подменяет график внутри
 * карточки: в карточке шириной в треть экрана одиннадцать колонок не помещаются.
 */
export default function FinanceSection({ days }: { days: DayFinance[] }) {
  const [enabled, setEnabled] = useState<Record<SeriesKey, boolean>>(INITIAL)
  const [tableOpen, setTableOpen] = useState(false)

  // Выключить всё нельзя: пустой график выглядит как поломка, а не как выбор.
  const activeCount = SERIES_ORDER.filter((k) => enabled[k]).length
  const toggle = (key: SeriesKey) => {
    if (enabled[key] && activeCount === 1) return
    setEnabled((prev) => ({ ...prev, [key]: !prev[key] }))
  }

  return (
    <>
      <motion.section
        initial={{ opacity: 0, y: 14 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.45, delay: 0.28, ease: [0.22, 1, 0.36, 1] }}
        className="rounded-2xl border border-slate-200/70 bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
      >
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2.5">
          <h2 className="text-[17px] font-extrabold tracking-tight text-slate-900">
            Динамика финансов
          </h2>

          <div className="order-last w-full sm:order-none sm:w-auto">
            <div className="flex w-fit flex-wrap items-center gap-3">
              {SERIES_ORDER.map((k) => {
                const cfg = SERIES[k]
                const active = enabled[k]
                return (
                  <button
                    key={k}
                    onClick={() => toggle(k)}
                    aria-pressed={active}
                    title={active ? `Скрыть: ${cfg.label}` : `Показать: ${cfg.label}`}
                    className={cn(
                      'text-[12.5px] font-bold transition-colors',
                      // Активный показатель — своего цвета, выключенный серый.
                      // Обводка и кружок-переключатель не нужны: сам цвет и есть
                      // состояние, как в легенде графика.
                      active ? 'hover:opacity-80' : 'text-slate-300 hover:text-slate-400',
                    )}
                    style={active ? { color: cfg.color } : undefined}
                  >
                    {cfg.label}
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

        <div className="mt-4">
          <FinanceChart days={days} enabled={enabled} />
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
