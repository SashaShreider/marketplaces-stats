import { ChevronRight, Info, TrendingDown, TrendingUp, Wallet } from 'lucide-react'
import { cn } from '@/utils/cn'
import type { ExpenseBreakdown, ExpenseCategory, ExpenseGroup, FinanceTotals } from '@/types'
import { AnimatedNumber, StatCard } from '@/components/ui'
import { fmtMoney } from '@/utils/format'

/**
 * Порядок статей в карточке расходов.
 *
 * <p>Задан списком на клиенте, а не берётся из ответа: сервер не перечисляет пустые
 * статьи, и без этого списка набор строк менялся бы от периода к периоду — статья
 * «Эквайринг» прыгала бы вверх в тот месяц, когда по ней что-то списали. Порядок
 * fixed и совпадает с {@code ExpenseCategory.displayOrder} на сервере.
 */
export const EXPENSE_ORDER: ExpenseCategory[] = [
  'COMMISSION',
  'PROMOTION',
  'LOGISTICS',
  'STORAGE',
  'ACQUIRING',
  'OTHER',
]

const LABELS: Record<ExpenseCategory, string> = {
  COMMISSION: 'Комиссия',
  PROMOTION: 'Продвижение',
  LOGISTICS: 'Логистика',
  STORAGE: 'Хранение и обработка',
  ACQUIRING: 'Эквайринг',
  OTHER: 'Прочие',
}

/** Статья в порядке карточки; пустая статья — с нулём, а не пропуском. */
export function orderedGroups(breakdown: ExpenseBreakdown | undefined): {
  category: ExpenseCategory
  label: string
  amount: number
  group: ExpenseGroup | null
}[] {
  const byCategory = new Map((breakdown?.groups ?? []).map((g) => [g.category, g]))
  return EXPENSE_ORDER.map((category) => {
    const group = byCategory.get(category) ?? null
    return {
      category,
      // Подпись берём из ответа, если статья есть: маркетплейс со своими
      // названиями подставит туда своё, и карточка не будет спорить с модалкой.
      label: group?.label ?? LABELS[category],
      // Расход показывается модулем: в ответе он отрицательный, а в карточке рядом
      // с «Расходы 19 868 ₽» минус читался бы как двойное отрицание.
      amount: Math.abs(group?.amount ?? 0),
      group,
    }
  })
}

function Row({
  label,
  value,
  negative,
  onClick,
}: {
  label: string
  value: number
  negative?: boolean
  onClick?: () => void
}) {
  const clickable = Boolean(onClick) && value !== 0
  const content = (
    <>
      <span className="flex min-w-0 items-center gap-1 text-[12.5px] font-medium text-slate-500">
        <span className="truncate">{label}</span>
      </span>
      <span className="flex shrink-0 items-center gap-1">
        <span className={cn('tnum text-[13px] font-bold', value === 0 && 'text-slate-300')}>
          {negative && value != 0 && '−'}
          <AnimatedNumber value={Math.abs(value)} />
        </span>
        {clickable && (
          <ChevronRight size={12} className="text-slate-300 transition-transform group-hover/row:translate-x-0.5 group-hover/row:text-brand-500" />
        )}
      </span>
    </>
  )

  if (!clickable) {
    return <div className="flex items-center justify-between py-[5px]">{content}</div>
  }
  return (
    <button
      onClick={onClick}
      title={`Расшифровка статьи «${label}»`}
      className="group/row -mx-1.5 flex w-[calc(100%+12px)] items-center justify-between rounded-lg px-1.5 py-[5px] text-left transition-colors hover:bg-white/70"
    >
      {content}
    </button>
  )
}

/**
 * Три карточки отчёта: доходы, расходы и прибыль.
 *
 * <p>В расходах — ровно шесть статей, и каждая раскрывается по клику: сумма без
 * расшифровки непроверяема. Список задан здесь, а не приходит из ответа, чтобы
 * порядок строк не зависел от того, какие статьи были в периоде.
 *
 * <p>Прибыль считается как доходы минус расходы. При отборе по товарам выплата
 * неизвестна, и тогда вместо неё показывается прочерк — не ноль.
 */
export default function SummaryCards({
  totals,
  breakdown,
  unallocated,
  scoped,
  payout,
  onExpenseGroup,
  onExpenseDetails,
}: {
  totals: FinanceTotals
  breakdown?: ExpenseBreakdown
  /** Расходы без привязки к товару, не вошедшие в отчёт при отборе */
  unallocated: number
  scoped: boolean
  /** К выплате; `null`, если отчёт ограничен отбором */
  payout: number | null
  onExpenseGroup: (category: ExpenseCategory) => void
  onExpenseDetails: () => void
}) {
  const groups = orderedGroups(breakdown)

  return (
    <div className="grid gap-4 md:grid-cols-3">
      <StatCard
        size="lg"
        delay={0.08}
        label="Доходы"
        value={<AnimatedNumber value={totals.income} />}
        tone="border-emerald-200/60 bg-gradient-to-b from-emerald-50/90 to-emerald-50/40"
        iconBg="bg-emerald-500/15 text-emerald-600"
        icon={<TrendingUp size={17} strokeWidth={2.5} />}
      >
        <Row label="Продажи" value={totals.sales} />
        <Row label="Возвраты" value={totals.returns} negative />
        <Row label="Бонусы" value={totals.partners} />
      </StatCard>

      <StatCard
        size="lg"
        delay={0.15}
        label="Расходы"
        value={<AnimatedNumber value={totals.expenses} />}
        tone="border-rose-200/60 bg-gradient-to-b from-rose-50/90 to-rose-50/40"
        iconBg="bg-rose-500/15 text-rose-500"
        icon={<TrendingDown size={17} strokeWidth={2.5} />}
      >
        {groups.map((g) => (
          <Row
            key={g.category}
            label={g.label}
            value={g.amount}
            onClick={() => onExpenseGroup(g.category)}
          />
        ))}
        <button
          onClick={onExpenseDetails}
          className="mt-1.5 flex w-full items-center justify-between gap-1 border-t border-slate-200/50 pt-2.5 text-[11.5px] font-bold text-brand-600 transition-colors hover:text-brand-700"
        >
          Детализация расходов
          <ChevronRight size={13} />
        </button>
      </StatCard>

      <StatCard
        size="lg"
        delay={0.22}
        label="Прибыль"
        value={<AnimatedNumber value={totals.profit} />}
        tone="border-brand-200/60 bg-gradient-to-b from-brand-50/90 to-brand-50/40"
        iconBg="bg-brand-500/15 text-brand-600"
        icon={<Wallet size={17} strokeWidth={2.5} />}
      >
        <Row label="Доходы" value={totals.income} />
        <Row label="Расходы" value={totals.expenses} negative />
        <div className="flex items-center justify-between border-t border-slate-200/50 pt-2.5">
          <span className="text-[12.5px] font-medium text-slate-500">К выплате</span>
          <span
            className="tnum text-[13px] font-bold text-slate-700"
            title={
              payout === null
                ? 'Выплата известна только по всему магазину: для выбранных товаров её долю из total_amount не выделить.'
                : undefined
            }
          >
            {payout === null ? '—' : <AnimatedNumber value={payout} />}
          </span>
        </div>
        {scoped && unallocated > 0 && (
          <div className="mt-2 flex items-start gap-1.5 rounded-lg bg-amber-50 px-2 py-1.5 text-[11px] font-semibold leading-relaxed text-amber-700 ring-1 ring-amber-200/70">
            <Info size={12} className="mt-0.5 shrink-0" />
            <span>
              За пределами отбора ещё {fmtMoney(unallocated)} расходов без привязки к товару
            </span>
          </div>
        )}
        <div className="mt-1.5 flex items-center gap-1.5 border-t border-slate-200/50 pt-2.5 text-[11px] font-medium text-slate-400">
          <Info size={12} />
          Себестоимость товаров не учтена
        </div>
      </StatCard>
    </div>
  )
}
