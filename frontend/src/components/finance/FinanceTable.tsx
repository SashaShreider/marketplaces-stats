import { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/utils/cn'
import type { DayFinance, DayState, FinanceTotals } from '@/types'
import { fmtDayShort, fmtMoney, fmtNum, sumDays, weekdayShort } from '@/utils/format'

const PAGE_SIZE = 30

/**
 * Разрядка колонки.
 *
 * <p>Вида ровно столько, сколько нужно, чтобы различать в строке четыре вещи:
 * обычное число, расход со знаком минус, итоговый итог (жирный) и прибыль.
 *
 * <p>Цветом выделена только прибыль — она единственная, где знак меняет смысл.
 * Расходы не красные: они и так видны по минусу, а красный цвет на четверти
 * таблицы превращался в фон и переставал читаться.
 *
 * <p>`income` и `expense` — те самые две колонки, ради которых выделение и нужно:
 * итог по доходам и итог по расходам. Обе жирные, и обе со знаком: у расходов
 * минус обязателен, иначе «Расходы 13 308 ₽» читается как ещё одна выручка.
 */
type ColKind = 'plain' | 'neg' | 'income' | 'expense' | 'profit' | 'qty'

const COLS: {
  label: string
  get: (d: FinanceTotals) => number
  kind: ColKind
}[] = [
  { label: 'Продажи', get: (d) => d.sales, kind: 'plain' },
  { label: 'Возвраты', get: (d) => d.returns, kind: 'neg' },
  { label: 'Бонусы', get: (d) => d.partners, kind: 'plain' },
  { label: 'Доходы', get: (d) => d.income, kind: 'income' },
  { label: 'Комиссия', get: (d) => d.commission, kind: 'neg' },
  { label: 'Логистика', get: (d) => d.logistics, kind: 'neg' },
  { label: 'Прочие расходы', get: (d) => d.other, kind: 'neg' },
  { label: 'Расходы', get: (d) => d.expenses, kind: 'expense' },
  { label: 'Прибыль', get: (d) => d.profit, kind: 'profit' },
  { label: 'Продано, шт', get: (d) => d.soldQty, kind: 'qty' },
  { label: 'Возвращено, шт', get: (d) => d.returnedQty, kind: 'qty' },
]

/** Расходные колонки: их значение показывается по модулю, со знаком минус. */
const SIGNED: ColKind[] = ['neg', 'expense']

const STATE_LABEL: Record<DayState, string | null> = {
  ok: null,
  provisional: 'Данные ещё могут уточниться',
  missing: 'Данные за этот день не загружены',
  failed: 'Не удалось загрузить данные за этот день',
}
const STATE_DOT: Record<DayState, string> = {
  ok: '',
  provisional: 'bg-sky-400',
  missing: 'bg-amber-400',
  failed: 'bg-rose-500',
}

function Cell({ value, kind, footer }: { value: number; kind: ColKind; footer?: boolean }) {
  const signed = SIGNED.includes(kind)
  return (
    <span
      className={cn(
        'tnum',
        // размер: обычные ячейки мельче, итоговая строка крупнее — она читается
        // как вывод, а не как ещё одна строка
        footer ? 'text-[12.5px]' : 'text-[12px]',
        // начертание: итоговые колонки и итоговая строка — жирные; остальное
        // одинаковое, иначе взгляд цепляется за каждую вторую цифру
        footer || kind === 'income' || kind === 'expense' || kind === 'profit'
          ? 'font-extrabold'
          : 'font-semibold',
        // цвет: только прибыль. Остальное нейтральное, чтобы взгляд шёл по строке,
        // а не перескакивал между красным и зелёным
        kind === 'profit' ? (value >= 0 ? 'text-emerald-600' : 'text-rose-600') : 'text-slate-700',
      )}
    >
      {kind === 'qty' ? (
        fmtNum(value)
      ) : (
        <>
          {signed && value > 0 && '−'}
          {fmtMoney(signed ? Math.abs(value) : value)}
        </>
      )}
    </span>
  )
}

export default function FinanceTable({ days }: { days: DayFinance[] }) {
  const [page, setPage] = useState(0)
  useEffect(() => setPage(0), [days.length, days[0]?.date])

  const pages = Math.max(1, Math.ceil(days.length / PAGE_SIZE))
  const safePage = Math.min(page, pages - 1)
  const rows = days.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE)
  const totals = sumDays(days)

  const firstShown = rows.length ? safePage * PAGE_SIZE + 1 : 0
  const lastShown = safePage * PAGE_SIZE + rows.length

  // Отступы и кегль ужаты: одиннадцать колонок должны помещаться без
  // горизонтальной прокрутки на обычном ноутбуке.
  const thCls =
    'sticky top-0 z-20 whitespace-nowrap border-b border-slate-200 bg-slate-50/95 px-2.5 py-2 text-right text-[10px] font-extrabold uppercase tracking-[0.06em] text-slate-400 backdrop-blur'
  const tdCls = 'whitespace-nowrap border-b border-slate-100 px-2.5 py-1.5 text-right group-hover:border-brand-100'

  return (
    <div className="overflow-hidden rounded-xl border border-slate-200/80">
      {/* общий скролл-контейнер: шапка и «Итого» прилипают */}
      <div className="scroll-slim max-h-[460px] overflow-auto">
        <table className="w-full min-w-[1060px] border-separate border-spacing-0">
          <thead>
            <tr>
              {/* z-30 обязателен: при горизонтальной прокрутке эта ячейка едет по шапке
                  и без повышенного слоя её накрывают соседние */}
              <th className={cn(thCls, 'left-0 z-30 text-left')}>Дата</th>
              {COLS.map((c) => (
                <th key={c.label} className={thCls}>
                  {c.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((d) => {
              const unloaded = d.state === 'missing' || d.state === 'failed'
              return (
                <tr key={d.date} className="group transition-colors hover:bg-brand-50/50">
                  <td
                    className={cn(tdCls, 'left-0 z-10 bg-white text-left group-hover:bg-brand-50/50')}
                    title={STATE_LABEL[d.state] ?? undefined}
                  >
                    <span className="inline-flex items-center gap-1.5">
                      {d.state !== 'ok' && (
                        <span className={cn('h-2 w-2 rounded-full', STATE_DOT[d.state])} />
                      )}
                      <span className="text-[12px] font-bold text-slate-800">{fmtDayShort(d.date)}</span>
                      <span className="text-[10.5px] font-semibold text-slate-400">
                        {weekdayShort(d.date)}
                      </span>
                    </span>
                  </td>
                  {unloaded ? (
                    <td
                      colSpan={COLS.length}
                      className={cn(tdCls, 'text-left text-[12px] font-semibold italic text-slate-400')}
                    >
                      {STATE_LABEL[d.state]}
                    </td>
                  ) : (
                    COLS.map((c) => (
                      <td key={c.label} className={tdCls}>
                        <Cell value={c.get(d)} kind={c.kind} />
                      </td>
                    ))
                  )}
                </tr>
              )
            })}
          </tbody>
          <tfoot>
            {/*
              Раньше ячейки этой строки наезжали на «Итого за период»: все td стояли на
              одном z-20, а прилипающая к левому краю ячейка объявлялась первой и
              оказывалась под остальными.

              Лечится тремя вещами сразу: непрозрачный фон (иначе строки подсвечивают
              сквозь него), изоляция слоя на самой строке и повышенный слой у первой
              ячейки — так же, как в шапке.
            */}
            {/*
              Строка итогов липнет только по вертикали (`bottom-0`) и НЕ по
              горизонтали: раньше первая ячейка держалась за левый край таблицы,
              а соседние уезжали — получалось, что подпись «Итого» висела на
              месте, а цифры рядом скользили под ней. Теперь вся строка ведёт себя
              одинаково.
            */}
            <tr>
              <td
                className={cn(
                  'sticky bottom-0 z-20 whitespace-nowrap border-t-2 border-slate-200 bg-slate-50 px-2.5 py-2 text-left text-[12.5px] font-extrabold text-slate-900',
                )}
              >
                Итого
              </td>
              {COLS.map((c) => (
                <td
                  key={c.label}
                  className="sticky bottom-0 z-20 whitespace-nowrap border-t-2 border-slate-200 bg-slate-50 px-2.5 py-2 text-right"
                >
                  <Cell value={c.get(totals)} kind={c.kind} footer />
                </td>
              ))}
            </tr>
          </tfoot>
        </table>
      </div>

      {/* сколько дней показано + пагинация */}
      <div className="flex items-center justify-between gap-3 border-t border-slate-100 px-4 py-2">
        <span className="text-[12px] font-medium text-slate-400">
          Показано{' '}
          <span className="tnum font-bold text-slate-600">{firstShown}</span>–{' '}
          <span className="tnum font-bold text-slate-600">{lastShown}</span> из{' '}
          <span className="tnum font-bold text-slate-600">{days.length}</span> дней
        </span>
        <div className="flex items-center gap-3">
          <span className="text-[12px] font-semibold text-slate-500">
            Страница <span className="tnum font-extrabold text-slate-800">{safePage + 1}</span> из{' '}
            <span className="tnum font-extrabold text-slate-800">{pages}</span>
          </span>
          <div className="flex gap-1">
            <button
              disabled={safePage === 0}
              onClick={() => setPage(safePage - 1)}
              title="Предыдущая страница"
              className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-all hover:border-brand-300 hover:text-brand-600 disabled:cursor-not-allowed disabled:opacity-35"
            >
            <ChevronLeft size={15} />
            </button>
            <button
              disabled={safePage >= pages - 1}
              onClick={() => setPage(safePage + 1)}
              title="Следующая страница"
              className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-all hover:border-brand-300 hover:text-brand-600 disabled:cursor-not-allowed disabled:opacity-35"
            >
              <ChevronRight size={15} />
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}