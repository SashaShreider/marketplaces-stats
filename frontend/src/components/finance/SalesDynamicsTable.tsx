import { useEffect, useState } from 'react'
import { cn } from '@/utils/cn'
import { Pager } from '@/components/ui'
import type { DayState } from '@/types'
import type { Point } from '@/components/finance/SalesDynamicsChart'
import { fmtDayShort, fmtMoney, fmtNum, weekdayShort } from '@/utils/format'

const PAGE_SIZE = 30

type ColKind = 'qty' | 'money' | 'expense' | 'revenue'

/**
 * Колонки таблицы: количество и деньги одного дня.
 *
 * <p>Деньги приходят со знаком, как их отдаёт OZON, поэтому показываются как есть:
 * расходы уже отрицательные, и подставлять минус вручную не нужно. Цветом выделена
 * только выручка — единственная величина, где знак меняет смысл.
 *
 * <p>Это экономика книги, а не магазина: все расходы здесь привязаны к SKU. Поэтому
 * выручка дня сходится с выручкой товара в таблице на этой же странице.
 */
const COLS: { label: string; get: (p: Point) => number; kind: ColKind; hint?: string }[] = [
  { label: 'Продано, шт', get: (p) => p.sold, kind: 'qty' },
  { label: 'Доход, ₽', get: (p) => p.income, kind: 'money' },
  { label: 'Комиссия, ₽', get: (p) => p.commission, kind: 'expense' },
  { label: 'Логистика, ₽', get: (p) => p.logistics, kind: 'expense' },
  {
    label: 'Продвижение, ₽',
    get: (p) => p.promotion,
    kind: 'expense',
    hint: 'Promotion + PayPerClick + StarsMembership — только начисления с привязкой к SKU',
  },
  {
    label: 'Другие расходы, ₽',
    get: (p) => p.otherExpenses,
    kind: 'expense',
    hint: 'Прочие списания по товару, кроме продвижения',
  },
  {
    label: 'Выручка, ₽',
    get: (p) => p.revenue,
    kind: 'revenue',
    hint: 'Доход минус расходы книги, включая продвижение',
  },
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
        'tnum',
        footer ? 'text-[12.5px]' : 'text-[12px]',
        footer || kind === 'revenue' ? 'font-extrabold' : 'font-semibold',
        kind === 'revenue' ? (value >= 0 ? 'text-emerald-600' : 'text-rose-600') : 'text-slate-700',
      )}
    >
      {kind === 'qty' ? fmtNum(value) : fmtMoney(value)}
    </span>
  )
}

/**
 * Таблица динамики продаж — второй вид того же раздела, что и график.
 *
 * <p>День — строка, итог за период закреплён снизу, дни разбиты по 30 на страницу,
 * а незагруженный день подписан словами, а не нулём: иначе «нет продаж» и «нет
 * данных» в таблице выглядели бы одинаково.
 *
 * <p>Колонки — экономика книги: доход, комиссия, логистика, продвижение и прочие
 * расходы, привязанные к SKU. Продвижение вынесено из прочих отдельной статьёй,
 * поэтому в «Других расходах» его уже нет — сумма статей равна расходам дня, а не
 * больше их.
 *
 * <p>Списания без привязки к товару (реклама, контейнеры) в таблицу не входят: их
 * нельзя отнести к книге, и выручка стала бы меньше заработка на ней. Их сумма за
 * период подписана под таблицей — ровно как на странице «Товары».
 *
 * <p>Данные приходят готовым рядом по всему периоду: дни без операций уже
 * восстановлены нулями, а их состояние проставлено. Поэтому таблица ничего не
 * запрашивает сама и не расходится с графиком.
 */
export default function SalesDynamicsTable({
  points,
  filterLabel,
  unallocatedExpenses,
  loading,
}: {
  points: Point[]
  /** Чем ограничен отбор: «Все товары», имя автора или «SKU …» */
  filterLabel: string
  /** Расходы периода без привязки к SKU, по модулю: в таблицу они не входят */
  unallocatedExpenses: number
  loading?: boolean
}) {
  const [page, setPage] = useState(0)
  useEffect(() => setPage(0), [points.length, points[0]?.date])

  const pages = Math.max(1, Math.ceil(points.length / PAGE_SIZE))
  const safePage = Math.min(page, pages - 1)
  const rows = points.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE)
  const totals = COLS.map((c) => points.reduce((sum, p) => sum + c.get(p), 0))

  const firstShown = rows.length ? safePage * PAGE_SIZE + 1 : 0
  const lastShown = safePage * PAGE_SIZE + rows.length

  const thCls =
    'sticky top-0 z-20 whitespace-nowrap border-b border-slate-200 bg-slate-50/95 px-2.5 py-2 text-right text-[10px] font-extrabold uppercase tracking-[0.06em] text-slate-400 backdrop-blur'
  const tdCls = 'whitespace-nowrap border-b border-slate-100 px-2.5 py-1.5 text-right group-hover:border-brand-100'

  if (points.length === 0) {
    return (
      <div className="flex h-[220px] items-center justify-center text-[13px] font-medium text-slate-400">
        Нет данных за выбранный период.
      </div>
    )
  }

  return (
    <div>
      <div
        className={cn(
          'overflow-hidden rounded-xl border border-slate-200/80',
          loading && 'opacity-60 transition-opacity',
        )}
      >
        {/* общий скролл-контейнер: шапка и «Итого» прилипают */}
        <div className="scroll-slim max-h-[460px] overflow-auto">
          <table className="w-full min-w-[900px] border-separate border-spacing-0">
            <thead>
              <tr>
                <th className={cn(thCls, 'left-0 z-30 text-left')}>Дата</th>
                {COLS.map((c) => (
                  <th key={c.label} className={thCls} title={c.hint}>
                    {c.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((p) => {
                const unloaded = p.state === 'missing' || p.state === 'failed'
                return (
                  <tr key={p.date} className="group transition-colors hover:bg-brand-50/50">
                    <td
                      className={cn(tdCls, 'left-0 z-10 bg-white text-left group-hover:bg-brand-50/50')}
                      title={STATE_LABEL[p.state] ?? undefined}
                    >
                      <span className="inline-flex items-center gap-1.5">
                        {p.state !== 'ok' && (
                          <span className={cn('h-2 w-2 rounded-full', STATE_DOT[p.state])} />
                        )}
                        <span className="text-[12px] font-bold text-slate-800">{fmtDayShort(p.date)}</span>
                        <span className="text-[10.5px] font-semibold text-slate-400">
                          {weekdayShort(p.date)}
                        </span>
                      </span>
                    </td>
                    {unloaded ? (
                      <td
                        colSpan={COLS.length}
                        className={cn(tdCls, 'text-left text-[12px] font-semibold italic text-slate-400')}
                      >
                        {STATE_LABEL[p.state]}
                      </td>
                    ) : (
                      COLS.map((c) => (
                        <td key={c.label} className={tdCls}>
                          <Cell value={c.get(p)} kind={c.kind} />
                        </td>
                      ))
                    )}
                  </tr>
                )
              })}
            </tbody>
            <tfoot>
              {/* Как в таблице на «Обзоре»: строка итогов липнет только по вертикали,
                  непрозрачный фон не даёт прокручиваемым строкам просвечивать. */}
              <tr>
                <td className="sticky bottom-0 z-20 whitespace-nowrap border-t-2 border-slate-200 bg-slate-50 px-2.5 py-2 text-left text-[12.5px] font-extrabold text-slate-900">
                  Итого
                </td>
                {COLS.map((c, i) => (
                  <td
                    key={c.label}
                    className="sticky bottom-0 z-20 whitespace-nowrap border-t-2 border-slate-200 bg-slate-50 px-2.5 py-2 text-right"
                  >
                    <Cell value={totals[i]} kind={c.kind} footer />
                  </td>
                ))}
              </tr>
            </tfoot>
          </table>
        </div>

        {/* сколько дней показано + отбор + пагинация */}
        <div className="flex flex-wrap items-center gap-3 border-t border-slate-100 px-4 py-2">
          <span className="text-[12px] font-medium text-slate-400">
            Показано{' '}
            <span className="tnum font-bold text-slate-600">{firstShown}</span>–{' '}
            <span className="tnum font-bold text-slate-600">{lastShown}</span> из{' '}
            <span className="tnum font-bold text-slate-600">{points.length}</span> дней
          </span>
          {filterLabel && (
            <span className="truncate text-[12px] font-medium text-slate-400">{filterLabel}</span>
          )}
          <div className="ml-auto flex items-center gap-3">
            <Pager page={safePage} pages={pages} onPageChange={setPage} />
          </div>
        </div>
      </div>

      {/* Списания без SKU нельзя отнести к книге. Молчать об этом нельзя: иначе
          расходы динамики выглядели бы меньше дневного отчёта без объяснения. */}
      <p className="mt-2 text-[11.5px] font-medium leading-relaxed text-slate-400">
        {unallocatedExpenses > 0 ? (
          <>
            В таблице — только расходы, привязанные к товарам. Списания без привязки к
            SKU (реклама, контейнеры и прочие услуги) относятся к магазину в целом: за
            период их{' '}
            <span className="tnum font-bold text-slate-500">
              {fmtMoney(unallocatedExpenses)}
            </span>{' '}
            — они видны в дневном отчёте на «Обзоре».
          </>
        ) : (
          <>
            В таблице — только расходы, привязанные к товарам: списаний без привязки к
            SKU за период не было.
          </>
        )}
      </p>
    </div>
  )
}
