import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Table2, Download } from 'lucide-react'
import type { DayFinance } from '@/types'
import FinanceChart, { SERIES, SERIES_ORDER, type SeriesKey } from '@/components/finance/FinanceChart'
import DailyFinanceTable, { exportFinanceCsv, groupByMonth } from '@/components/finance/DailyFinanceTable'
import Modal from '@/components/ui/Modal'
import { SecondaryButton } from '@/components/ui/controls'
import { cn } from '@/utils/cn'

/**
 * Что включено при открытии.
 *
 * <p>Расходы выключены: их сумма в разы больше продаж и прибыли, и линия
 * расходов прижимала остальные к нижнему краю — по графику нельзя было прочитать
 * ни форму продаж, ни прибыль. Включить их — один клик в легенде.
 */
const INITIAL: Record<SeriesKey, boolean> = {
  income: true,
  expenses: false,
  profit: true,
  qty: true,
}

/**
 * Динамика финансов за период.
 *
 * <p>Показатели включаются независимо, а не переключаются по одному: на одном
 * графике видно и продажи, и прибыль, и связь между ними очевидна — тогда как
 * режим «одна линия за раз» заставлял держать разницу в голове.
 *
 * <p>График и таблица — два разных способа смотреть: по графику видно форму
 * периода, по таблице — конкретные числа с расшифровкой расходов. Таблица
 * открывается отдельным окном почти на весь экран: одиннадцать колонок в
 * карточке не помещаются.
 */
export default function FinanceSection({
  days,
  filters,
}: {
  days: DayFinance[]
  /**
   * Фильтры отчёта (дата, автор, товары) — показываются в окне таблицы.
   *
   * <p>Готовым узлом, а не набором полей: карточка графика ничего не знает про
   * период и отбор, она рисует то, что ей дали. Иначе пришлось бы протащить сюда
   * весь набор состояний страницы и повторять их разметку.
   */
  filters?: React.ReactNode
}) {
  const [enabled, setEnabled] = useState<Record<SeriesKey, boolean>>(INITIAL)
  const [tableOpen, setTableOpen] = useState(false)
  const [page, setPage] = useState(0)
  const [mode, setMode] = useState<TableMode>('detailed')
  const [step, setStep] = useState<Step>('days')

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
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
          <h2 className="text-[17px] font-extrabold tracking-tight text-slate-900">
            Динамика финансов
          </h2>

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

        {/* Легенда — своей строкой под названием, а не в одну с ним: рядом с
            заголовком она сжималась на узком экране в нечитаемую полосу, а
            перенос шёл посреди подписей. Здесь у неё есть вся ширина. */}
        <div className="mt-2.5 flex flex-wrap items-center gap-3">
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

        <div className="mt-4">
          <FinanceChart days={days} enabled={enabled} />
        </div>
      </motion.section>

      <Modal
        open={tableOpen}
        title="Динамика финансов"
        onClose={() => setTableOpen(false)}
        size="xl"
        headerExtra={<div className="flex flex-wrap items-center gap-2">{filters}</div>}
        headerBelow={
          <ViewOptions
            mode={mode}
            onModeChange={setMode}
            step={step}
            onStepChange={setStep}
          />
        }
        footer={
          <>
            <SecondaryButton
              onClick={() => exportFinanceCsv(step === 'months' ? groupByMonth(days) : days, days, mode === 'compact')}
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
        <DailyFinanceTable
          days={days}
          page={page}
          onPageChange={setPage}
          compact={mode === 'compact'}
          byMonth={step === 'months'}
        />
      </Modal>
    </>
  )
}

/** Режим отображения таблицы: с расшифровкой расходов или одной строкой. */
type TableMode = 'detailed' | 'compact'

/** Шаг строк: дни периода или свёрнутые месяцы. */
type Step = 'days' | 'months'

const VIEW_MODES: { id: TableMode; label: string }[] = [
  { id: 'detailed', label: 'Подробный вид' },
  { id: 'compact', label: 'Компактный вид' },
]

const STEP_MODES: { id: Step; label: string }[] = [
  { id: 'days', label: 'По дням' },
  { id: 'months', label: 'По месяцам' },
]

/**
 * Переключатели вида таблицы.
 *
 * <p>Детализация — подробный вид с раскладкой расходов по статьям или компактный
 * одной строкой. Шаг строк — день или месяц: во втором случае дни складываются в
 * строку месяца.
 *
 * <p>Это две независимые настройки, и выглядят они двумя отдельными группами, а не
 * одним рядом кнопок: раньше «Компактный вид» и «По дням» стояли вплотную, и
 * казалось, что выбрать можно что-то одно из четырёх. Каждая группа обведена и
 * подписана, между ними — воздух.
 */
function ViewOptions({
  mode,
  onModeChange,
  step,
  onStepChange,
}: {
  mode: TableMode
  onModeChange: (mode: TableMode) => void
  step: Step
  onStepChange: (step: Step) => void
}) {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
      <Segmented
        label="Детализация"
        items={VIEW_MODES}
        value={mode}
        onChange={onModeChange}
      />
      <Segmented label="Шаг" items={STEP_MODES} value={step} onChange={onStepChange} />
    </div>
  )
}

/**
 * Группа взаимоисключающих кнопок.
 *
 * <p>Обведена и подписана снаружи: подпись внутри рамки спорила бы с подписями
 * кнопок, а без подписи две группы читались бы как один список из четырёх
 * пунктов. Выбранная кнопка — белая на общем фоне, как в остальных переключателях
 * интерфейса.
 */
function Segmented<T extends string>({
  label,
  items,
  value,
  onChange,
}: {
  label: string
  items: { id: T; label: string }[]
  value: T
  onChange: (value: T) => void
}) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-[10.5px] font-bold uppercase tracking-[0.08em] text-slate-400">
        {label}
      </span>
      <div
        role="group"
        aria-label={label}
        className="flex items-center gap-0.5 rounded-xl border border-slate-200 bg-slate-50/70 p-0.5"
      >
        {items.map((item) => {
          const active = item.id === value
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => onChange(item.id)}
              aria-pressed={active}
              className={cn(
                'rounded-[10px] px-2.5 py-1 text-[11.5px] font-bold transition-colors',
                active
                  ? 'bg-white text-slate-800 shadow-sm ring-1 ring-slate-200/80'
                  : 'text-slate-400 hover:text-slate-600',
              )}
            >
              {item.label}
            </button>
          )
        })}
      </div>
    </div>
  )
}
