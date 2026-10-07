import { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/utils/cn'
import type { DayFinance, DayState, FinanceTotals } from '@/types'
import { fmtDayShort, fmtMoney, fmtNum, sumDays, weekdayShort } from '@/utils/format'

const PAGE_SIZE = 15

type ColKind = 'plain' | 'neg' | 'strong' | 'profit' | 'qty'

const COLS: {
  label: string
  get: (d: FinanceTotals) => number
  kind: ColKind
}[] = [
  { label: 'Продажи', get: (d) => d.sales, kind: 'plain' },
  { label: 'Возвраты', get: (d) => d.returns, kind: 'neg' },
  { label: 'Партнёры', get: (d) => d.partners, kind: 'plain' },
  { label: 'Доходы', get: (d) => d.income, kind: 'strong' },
  { label: 'Комиссия', get: (d) => d.commission, kind: 'neg' },
  { label: 'Логистика', get: (d) => d.logistics, kind: 'neg' },
  { label: 'Прочие расходы', get: (d) => d.other, kind: 'neg' },
  { label: 'Расходы', get: (d) => d.expenses, kind: 'strong' },
  { label: 'Прибыль', get: (d) => d.profit, kind: 'profit' },
  { label: 'Продано, шт', get: (d) => d.soldQty, kind: 'qty' },
  { label: 'Возвращено, шт', get: (d) => d.returnedQty, kind: 'qty' },
]

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
  return (
    <span
      className={cn(
        'tnum text-[12.5px]',
        kind === 'neg' && 'font-semibold text-rose-500',
        kind === 'plain' && 'font-semibold text-slate-600',
        kind === 'qty' && 'font-semibold text-slate-500',
        kind === 'strong' && 'font-extrabold text-slate-900',
        kind === 'profit' && (value >= 0 ? 'font-extrabold text-emerald-600' : 'font-extrabold text-rose-500'),
        footer && 'text-[13px]',
      )}
    >
      {kind === 'qty' ? (
        fmtNum(value)
      ) : (
        <>
          {kind === 'neg' && value > 0 && '−'}
          {fmtMoney(kind === 'neg' ? Math.abs(value) : value)}
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

  const thCls =
    'sticky top-0 z-20 whitespace-nowrap border-b border-slate-200 bg-slate-50/95 px-4 py-3 text-right text-[10.5px] font-extrabold uppercase tracking-[0.08em] text-slate-400 backdrop-blur'
  const tfCls =
    'sticky bottom-0 z-20 whitespace-nowrap border-t-2 border-slate-200 bg-slate-50 px-4 py-3 text-right'

  return (
    <div className="overflow-hidden rounded-xl border border-slate-200/80">
      {/* общий скролл-контейнер: шапка и «Итого» прилипают */}
      <div className="scroll-slim max-h-[428px] overflow-auto">
        <table className="w-full min-w-[1280px] border-separate border-spacing-0">
          <thead>
            <tr>
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
                    className="whitespace-nowrap border-b border-slate-100 px-4 py-2.5 group-hover:border-brand-100"
                    title={STATE_LABEL[d.state] ?? undefined}
                  >
                    <span className="inline-flex items-center gap-2">
                      {d.state !== 'ok' && (
                        <span className={cn('h-2 w-2 rounded-full', STATE_DOT[d.state])} />
                      )}
                      <span className="text-[12.5px] font-bold text-slate-800">{fmtDayShort(d.date)}</span>
                      <span className="text-[11px] font-semibold text-slate-400">{weekdayShort(d.date)}</span>
                    </span>
                  </td>
                  {unloaded ? (
                    <td
                      colSpan={COLS.length}
                      className="border-b border-slate-100 px-4 py-2.5 text-left text-[12px] font-semibold italic text-slate-400 group-hover:border-brand-100"
                    >
                      {STATE_LABEL[d.state]}
                    </td>
                  ) : (
                    COLS.map((c) => (
                      <td
                        key={c.label}
                        className="whitespace-nowrap border-b border-slate-100 px-4 py-2.5 text-right group-hover:border-brand-100"
                      >
                        <Cell value={c.get(d)} kind={c.kind} />
                      </td>
                    ))
                  )}
                </tr>
              )
            })}
          </tbody>
          <tfoot>
            <tr>
              <td
                className={cn(
                  tfCls,
                  'left-0 text-left text-[11px] font-extrabold uppercase tracking-[0.08em] text-slate-500',
                )}
              >
                Итого за период
              </td>
              {COLS.map((c) => (
                <td key={c.label} className={tfCls}>
                  <Cell value={c.get(totals)} kind={c.kind} footer />
                </td>
              ))}
            </tr>
          </tfoot>
        </table>
      </div>

      {/* пагинация */}
      <div className="flex items-center justify-between gap-3 border-t border-slate-100 px-4 py-2 sm:justify-end">
        <span className="text-[12px] font-semibold text-slate-500">
          Страница <span className="tnum font-extrabold text-slate-800">{safePage + 1}</span> из{' '}
          <span className="tnum font-extrabold text-slate-800">{pages}</span>
        </span>
        <div className="flex gap-1">
          <button
            disabled={safePage === 0}
            onClick={() => setPage(safePage - 1)}
            className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-all hover:border-brand-300 hover:text-brand-600 disabled:cursor-not-allowed disabled:opacity-35"
          >
            <ChevronLeft size={15} />
          </button>
          <button
            disabled={safePage >= pages - 1}
            onClick={() => setPage(safePage + 1)}
            className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-all hover:border-brand-300 hover:text-brand-600 disabled:cursor-not-allowed disabled:opacity-35"
          >
            <ChevronRight size={15} />
          </button>
        </div>
      </div>
    </div>
  )
}
