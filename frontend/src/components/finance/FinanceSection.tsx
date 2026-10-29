import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Table2, Download } from 'lucide-react'
import type { DayFinance } from '@/types'
import FinanceChart, { SERIES, SERIES_ORDER, type SeriesKey } from '@/components/finance/FinanceChart'
import DailyFinanceTable, { exportDailyCsv } from '@/components/finance/DailyFinanceTable'
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
export default function FinanceSection({
  days,
  scope,
}: {
  days: DayFinance[]
  /** Подпись периода и отбора: «01.09.2026 — 30.09.2026 · Сурцуков А.» */
  scope?: string
}) {
  const [enabled, setEnabled] = useState<Record<SeriesKey, boolean>>(INITIAL)
  const [tableOpen, setTableOpen] = useState(false)
  const [page, setPage] = useState(0)

  // Сменился период — первая страница. Иначе можно остаться на десятой, которой
  // в новом, более коротком периоде уже нет: таблица показала бы пустоту.
  useEffect(() => {
    setPage(0)
  }, [days])

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
                      'flex items-center gap-1.5 text-[12.5px] font-bold transition-colors',
                      // Текст всегда тёмный, а состояние читается по кружку: он
                      // цветной у включённого показателя и серый у выключенного.
                      // Красить сам текст в цвет серии значило бы спорить с
                      // легендой графика, где подписи нейтральные.
                      active ? 'text-slate-800 hover:text-slate-500' : 'text-slate-300 hover:text-slate-400',
                    )}
                  >
                    <span
                      className="h-2 w-2 shrink-0 rounded-full transition-colors"
                      style={{ background: active ? cfg.color : '#cbd5e1' }}
                    />
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
        subtitle={scope}
        onClose={() => setTableOpen(false)}
        size="xl"
        footer={
          <>
            <SecondaryButton
              onClick={() => exportDailyCsv(days)}
              disabled={days.length === 0}
              title="Выгрузить таблицу в CSV"
            >
              <Download size={13} />
              Экспорт в CSV
            </SecondaryButton>
            <button
              onClick={() => setTableOpen(false)}
              className="ml-auto rounded-xl border border-slate-200 px-4 py-2 text-[12.5px] font-bold text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-800"
            >
              Закрыть
            </button>
          </>
        }
      >
        <DailyFinanceTable days={days} page={page} onPageChange={setPage} />
      </Modal>
    </>
  )
}
