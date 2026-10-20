import { Info, TrendingDown, TrendingUp, Wallet } from 'lucide-react'
import { cn } from '@/utils/cn'
import type { FinanceTotals } from '@/types'
import { AnimatedNumber, StatCard } from '@/components/ui'

function Row({
  label,
  value,
  negative,
  className,
}: {
  label: string
  value: number
  negative?: boolean
  className?: string
}) {
  return (
    <div className={cn('flex items-center justify-between py-[5px]', className)}>
      <span className="text-[12.5px] font-medium text-slate-500">{label}</span>
      <span className={cn('tnum text-[13px] font-bold')}>
        {negative && value != 0 && '−'}
        <AnimatedNumber value={Math.abs(value)} />
      </span>
    </div>
  )
}

export default function SummaryCards({ totals }: { totals: FinanceTotals }) {
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
        <Row label="Комиссия" value={totals.commission} />
        <Row label="Логистика" value={totals.logistics} />
        <Row label="Прочие расходы" value={totals.other} />
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
        <div className="mt-1.5 flex items-center gap-1.5 border-t border-slate-200/50 pt-2.5 text-[11px] font-medium text-slate-400">
          <Info size={12} />
          Себестоимость товаров не учтена
        </div>
      </StatCard>
    </div>
  )
}
