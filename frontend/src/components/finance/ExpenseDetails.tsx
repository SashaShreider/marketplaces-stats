import type { ExpenseCategory, ExpenseGroup } from '@/types'
import Modal from '@/components/ui/Modal'
import { DetailRow } from '@/components/ui/controls'
import { fmtMoney2, fmtPercent } from '@/utils/format'
import { orderedGroups } from '@/components/finance/SummaryCards'

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
      subtitle={
        category === null
          ? undefined
          : 'Из чего сложилась статья. Проценты считаются от её суммы.'
      }
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
 * Детализация расходов целиком: доли статей от доходов и от расходов.
 *
 * <p>Две базы, а не одна, потому что вопросов у продавца два: «сколько у меня
 * забирают от выручки» и «куда уходит то, что забирают». Процент от доходов может
 * быть больше 100 — так бывает, когда расходы превысили доходы, — и это не ошибка
 * отображения.
 *
 * <p>С прошлым периодом намеренно не сравнивается: отчёт считается за выбранный
 * период, и «стало лучше / хуже» без второго периода было бы выдумкой.
 */
export function ExpenseDetailsModal({
  open,
  breakdown,
  income,
  expenses,
  scoped,
  unallocated,
  onClose,
  onGroup,
}: {
  open: boolean
  breakdown: { groups: ExpenseGroup[]; total: number } | undefined
  income: number
  expenses: number
  scoped: boolean
  unallocated: number
  onClose: () => void
  onGroup: (category: ExpenseCategory) => void
}) {
  const groups = orderedGroups(breakdown).filter((g) => g.amount !== 0)
  const total = Math.abs(breakdown?.total ?? 0) || expenses
  const totalShare = income === 0 ? undefined : total / income

  return (
    <Modal
      open={open}
      title="Детализация расходов"
      subtitle="Доля каждой статьи — от доходов и от всех расходов периода."
      onClose={onClose}
      size="lg"
      footer={
        <button
          onClick={onClose}
          className="rounded-xl border border-slate-200 px-4 py-2 text-[12.5px] font-bold text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-800"
        >
          Закрыть
        </button>
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
                { label: 'От доходов', align: 'right' },
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
            {groups.map((g) => (
              <tr
                key={g.category}
                onClick={() => onGroup(g.category)}
                title={`Расшифровка статьи «${g.label}»`}
                className="group cursor-pointer transition-colors hover:bg-brand-50/60"
              >
                <td className="border-b border-slate-100 px-3 py-2 text-left">
                  <span className="text-[12.5px] font-semibold text-slate-700 group-hover:text-brand-700">
                    {g.label}
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
            ))}
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

      {scoped && unallocated > 0 && (
        <p className="mt-3 rounded-xl bg-amber-50 px-3.5 py-2.5 text-[11.5px] font-semibold leading-relaxed text-amber-700 ring-1 ring-amber-200/70">
          В отчёт не вошли расходы без привязки к товару — ещё {fmtMoney2(unallocated)}. Их нельзя
          разделить между выбранными товарами, поэтому проценты считаются только от того, что
          относится к отбору.
        </p>
      )}

      <p className="mt-3 text-[11px] font-medium leading-relaxed text-slate-400">
        Проценты от доходов могут превышать 100 %: так бывает, когда расходы больше доходов за
        период. С прошлым периодом расходы не сравниваются — сравнение требует второго отчёта.
      </p>
    </Modal>
  )
}
