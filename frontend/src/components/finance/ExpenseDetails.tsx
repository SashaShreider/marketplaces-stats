import { Fragment, useEffect, useState } from 'react'
import { ChevronRight, Download } from 'lucide-react'
import type { DateRange, ExpenseCategory, ExpenseGroup } from '@/types'
import Modal from '@/components/ui/Modal'
import { DetailRow, SecondaryButton } from '@/components/ui/controls'
import { cn } from '@/utils/cn'
import { downloadCsv, safeFilename, toCsv } from '@/utils/csv'
import { fmtMoney2, fmtPercent } from '@/utils/format'
import { orderedGroups } from '@/components/finance/SummaryCards'

/** Округление перед выгрузкой: в CSV числа сырые и хвост плавающей точки виден. */
function round2(n: number): number {
  return Math.round(n * 100) / 100
}

/** Процент в пунктах для CSV; пусто, если база нулевая. */
function pct(value: number, base: number): number | null {
  return base === 0 ? null : round2((value / base) * 100)
}

/** Составляющие статьи в порядке убывания суммы — том же, что в таблице. */
function sortedItems(group: ExpenseGroup | null): ExpenseGroup['items'] {
  return [...(group?.items ?? [])].sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount))
}

/**
 * Расшифровка одной статьи расходов по типам начислений.
 *
 * <p>Статья — это сумма; проверить её можно только по слагаемым. Поэтому проценты
 * здесь считаются от суммы статьи, а не от всех расходов: тогда доли внутри статьи
 * складываются в 100 %, и видно, что именно её составляет.
 *
 * <p>Если статья пустая (расходов по ней не было), окно всё равно открывается и
 * говорит об этом прямо: молчание по клику читалось бы как поломка.
 */
export default function ExpenseGroupModal({
  category,
  group,
  breakdown,
  onClose,
}: {
  category: ExpenseCategory | null
  group: ExpenseGroup | null
  breakdown: { groups: ExpenseGroup[]; total: number } | undefined
  onClose: () => void
}) {
  const known = orderedGroups(breakdown).find((g) => g.category === category)
  const label = group?.label ?? known?.label ?? ''
  const total = Math.abs(group?.amount ?? 0)
  const items = [...(group?.items ?? [])].sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount))

  return (
    <Modal
      open={category !== null}
      title={label || 'Статья расходов'}
      onClose={onClose}
      size="md"
    >
      {items.length === 0 ? (
        <p className="py-6 text-center text-[12.5px] font-medium text-slate-500">
          По этой статье расходов за период не было.
        </p>
      ) : (
        <>
          {items.map((item, i) => (
            <DetailRow
              key={`${item.typeId ?? 'none'}-${i}`}
              label={item.description || item.name}
              hint={
                // Служебное имя нужно, когда человеческое описание совпадает с
                // подписью статьи или его нет вовсе: по нему статью и находят в
                // кабинете OZON.
                item.description && item.description !== item.name
                  ? item.name
                  : item.typeId === null
                    ? null
                    : `Тип начисления ${item.typeId}`
              }
              amount={item.amount}
              share={total === 0 ? undefined : Math.abs(item.amount) / total}
            />
          ))}
          <div className="mt-3 flex items-baseline justify-between border-t-2 border-slate-200 pt-2.5">
            <span className="text-[12.5px] font-extrabold text-slate-900">Итого по статье</span>
            <span className="tnum text-[13px] font-extrabold text-slate-900">
              {total === 0 ? '—' : fmtMoney2(total)}
            </span>
          </div>
        </>
      )}
    </Modal>
  )
}

/**
 * Детализация расходов целиком: доли статей от продаж и от расходов.
 *
 * <p>Две базы, а не одна, потому что вопросов у продавца два: «сколько у меня
 * забирают от продаж» и «куда уходит то, что забирают». Процент от продаж может
 * быть больше 100 — так бывает, когда расходы превысили продажи, — и это не ошибка
 * отображения.
 *
 * <p>С прошлым периодом намеренно не сравнивается: отчёт считается за выбранный
 * период, и «стало лучше / хуже» без второго периода было бы выдумкой.
 *
 * <p>Статья раскрывается прямо в таблице: расшифровка остаётся рядом со своей
 * суммой и с теми же колонками процентов, и не нужно закрывать одно окно, чтобы
 * открыть другое.
 */
export function ExpenseDetailsModal({
  open,
  breakdown,
  income,
  expenses,
  range,
  author,
  skus,
  onClose,
}: {
  open: boolean
  breakdown: { groups: ExpenseGroup[]; total: number } | undefined
  income: number
  expenses: number
  range: DateRange
  author: string
  skus: number[]
  onClose: () => void
}) {
  const [expanded, setExpanded] = useState<ExpenseCategory | null>(null)
  const groups = orderedGroups(breakdown).filter((g) => g.amount !== 0)
  const total = Math.abs(breakdown?.total ?? 0) || expenses
  const totalShare = income === 0 ? undefined : total / income

  useEffect(() => {
    if (!open) setExpanded(null)
  }, [open])

  const exportCsv = () => {
    const rows: (string | number | null)[][] = [
      ['Статья расходов', 'Детализация', 'Сумма', 'От расходов, %', 'От продаж, %'],
    ]
    for (const g of groups) {
      for (const item of sortedItems(g.group)) {
        const amount = Math.abs(item.amount)
        rows.push([
          g.label,
          item.description || item.name,
          round2(amount),
          pct(amount, total),
          pct(amount, income),
        ])
      }
      // Последняя строка статьи — её итог: по нему статью сверяют с карточкой.
      rows.push([
        g.label,
        'Итого по статье',
        round2(g.amount),
        pct(g.amount, total),
        pct(g.amount, income),
      ])
    }
    rows.push([
      'Всего расходов',
      '',
      round2(total),
      total === 0 ? 0 : 100,
      pct(total, income),
    ])
    downloadCsv(
      safeFilename([
        'Обзор',
        'детализация-расходов',
        `${range.from}_${range.to}`,
        author ? `автор-${author}` : 'все-авторы',
        skus.length ? `SKU-${skus.join('-')}` : 'все-товары',
      ]),
      toCsv(rows),
    )
  }

  return (
    <Modal
      open={open}
      title="Детализация расходов"
      onClose={onClose}
      size="lg"
      footer={
        <>
          <div className="mr-auto">
            <SecondaryButton
              onClick={exportCsv}
              disabled={groups.length === 0}
              title="Выгрузить детализацию в CSV"
            >
              <Download size={13} />
              Экспорт в CSV
            </SecondaryButton>
          </div>
          <button
            onClick={onClose}
            className="rounded-xl border border-slate-200 px-4 py-2 text-[12.5px] font-bold text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-800"
          >
            Закрыть
          </button>
        </>
      }
    >
      <div className="overflow-hidden rounded-xl border border-slate-200/80">
        <table className="w-full border-separate border-spacing-0">
          <thead>
            <tr>
              {[
                { label: 'Статья', align: 'left' },
                { label: 'Сумма', align: 'right' },
                { label: 'От расходов', align: 'right' },
                { label: 'От продаж', align: 'right' },
              ].map((c) => (
                <th
                  key={c.label}
                  className="whitespace-nowrap border-b border-slate-200 bg-slate-50/95 px-3 py-2 text-[10px] font-extrabold uppercase tracking-[0.06em] text-slate-400"
                  style={{ textAlign: c.align as 'left' | 'right' }}
                >
                  {c.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {groups.length === 0 && (
              <tr>
                <td colSpan={4} className="px-3 py-6 text-center text-[12.5px] font-medium text-slate-500">
                  За период расходов не было.
                </td>
              </tr>
            )}
            {groups.map((g) => {
              const isOpen = expanded === g.category
              const items = sortedItems(g.group)
              return (
                <Fragment key={g.category}>
                  <tr
                    onClick={() => setExpanded(isOpen ? null : g.category)}
                    title={`Расшифровка статьи «${g.label}»`}
                    aria-expanded={isOpen}
                    className={cn(
                      'group cursor-pointer transition-colors hover:bg-brand-50/60',
                      isOpen && 'bg-brand-50/40',
                    )}
                  >
                    <td className="border-b border-slate-100 px-3 py-2 text-left">
                      <span className="flex items-center gap-1.5">
                        <ChevronRight
                          size={12}
                          className={cn(
                            'shrink-0 text-slate-400 transition-transform group-hover:text-brand-600',
                            isOpen && 'rotate-90 text-brand-600',
                          )}
                        />
                        <span className="text-[12.5px] font-semibold text-slate-700 group-hover:text-brand-700">
                          {g.label}
                        </span>
                      </span>
                    </td>
                    <td className="tnum border-b border-slate-100 px-3 py-2 text-right text-[12.5px] font-bold text-slate-700">
                      {fmtMoney2(g.amount)}
                    </td>
                    <td className="tnum border-b border-slate-100 px-3 py-2 text-right text-[12px] font-semibold text-slate-500">
                      {fmtPercent(total === 0 ? 0 : g.amount / total)}
                    </td>
                    <td className="tnum border-b border-slate-100 px-3 py-2 text-right text-[12px] font-semibold text-slate-500">
                      {income === 0 ? '—' : fmtPercent(g.amount / income)}
                    </td>
                  </tr>

                  {isOpen &&
                    (items.length === 0 ? (
                      <tr>
                        <td
                          colSpan={4}
                          className="border-b border-slate-100 bg-slate-50/70 px-3 py-3 pl-9 text-[11.5px] font-medium text-slate-500"
                        >
                          По этой статье расходов за период не было.
                        </td>
                      </tr>
                    ) : (
                      items.map((item, i) => (
                        <tr
                          key={`${item.typeId ?? 'none'}-${i}`}
                          className="bg-slate-50/70"
                        >
                          <td className="border-b border-slate-100 px-3 py-2 pl-9 text-left">
                            <span className="text-[12px] font-medium text-slate-600">
                              {item.description || item.name}
                            </span>
                          </td>
                          <td className="tnum border-b border-slate-100 px-3 py-2 text-right text-[12px] font-semibold text-slate-600">
                            {item.amount < 0 ? '−' : item.amount > 0 ? '+' : ''}
                            {fmtMoney2(Math.abs(item.amount))}
                          </td>
                          <td className="tnum border-b border-slate-100 px-3 py-2 text-right text-[11.5px] font-semibold text-slate-500">
                            {fmtPercent(total === 0 ? 0 : Math.abs(item.amount) / total)}
                          </td>
                          <td className="tnum border-b border-slate-100 px-3 py-2 text-right text-[11.5px] font-semibold text-slate-500">
                            {income === 0 ? '—' : fmtPercent(Math.abs(item.amount) / income)}
                          </td>
                        </tr>
                      ))
                    ))}
                </Fragment>
              )
            })}
          </tbody>
          <tfoot>
            <tr>
              <td className="border-t-2 border-slate-200 bg-slate-50 px-3 py-2 text-left text-[12.5px] font-extrabold text-slate-900">
                Всего расходов
              </td>
              <td className="tnum border-t-2 border-slate-200 bg-slate-50 px-3 py-2 text-right text-[12.5px] font-extrabold text-slate-900">
                {fmtMoney2(total)}
              </td>
              <td className="tnum border-t-2 border-slate-200 bg-slate-50 px-3 py-2 text-right text-[12px] font-extrabold text-slate-700">
                {fmtPercent(total === 0 ? 0 : 1)}
              </td>
              <td className="tnum border-t-2 border-slate-200 bg-slate-50 px-3 py-2 text-right text-[12px] font-extrabold text-slate-700">
                {fmtPercent(totalShare ?? 0)}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
    </Modal>
  )
}
