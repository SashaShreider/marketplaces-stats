import { motion } from 'framer-motion'
import { Info, TrendingDown, TrendingUp, Wallet } from 'lucide-react'
import { cn } from '@/utils/cn'
import type { FinanceTotals } from '@/types'
import { AnimatedNumber } from '@/components/ui'

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

function Card({
  index,
  tint,
  iconBg,
  icon,
  title,
  amount,
  children,
}: {
  index: number
  tint: string
  iconBg: string
  icon: React.ReactNode
  title: string
  amount: number
  children: React.ReactNode
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, delay: 0.08 + index * 0.07, ease: [0.22, 1, 0.36, 1] }}
      className={cn('rounded-2xl border p-5', tint)}
    >
      <div className="flex items-center gap-2.5">
        <span className={cn('flex h-9 w-9 items-center justify-center rounded-xl', iconBg)}>{icon}</span>
        <span className="text-[11.5px] font-extrabold uppercase tracking-[0.1em] text-slate-500">{title}</span>
      </div>
      <div className="mt-3.5 text-[30px] font-extrabold leading-none tracking-tight text-slate-900">
        <AnimatedNumber value={amount} />
      </div>
      <div className="mt-4">{children}</div>
    </motion.div>
  )
}

export default function SummaryCards({ totals }: { totals: FinanceTotals }) {
  return (
    <div className="grid gap-4 md:grid-cols-3">
      <Card
        index={0}
        title="Доходы"
        amount={totals.income}
        tint="border-emerald-200/60 bg-gradient-to-b from-emerald-50/90 to-emerald-50/40"
        iconBg="bg-emerald-500/15 text-emerald-600"
        icon={<TrendingUp size={17} strokeWidth={2.5} />}
      >
        <Row label="Продажи" value={totals.sales} />
        <Row label="Возвраты" value={totals.returns} negative />
        <Row label="Программы партнёров" value={totals.partners} />
      </Card>

      <Card
        index={1}
        title="Расходы"
        amount={totals.expenses}
        tint="border-rose-200/60 bg-gradient-to-b from-rose-50/90 to-rose-50/40"
        iconBg="bg-rose-500/15 text-rose-500"
        icon={<TrendingDown size={17} strokeWidth={2.5} />}
      >
        <Row label="Комиссия" value={totals.commission} />
        <Row label="Логистика" value={totals.logistics} />
        <Row label="Прочие расходы" value={totals.other} />
      </Card>

      <Card
        index={2}
        title="Прибыль"
        amount={totals.profit}
        tint="border-brand-200/60 bg-gradient-to-b from-brand-50/90 to-brand-50/40"
        iconBg="bg-brand-500/15 text-brand-600"
        icon={<Wallet size={17} strokeWidth={2.5} />}
      >
        <Row label="Доходы" value={totals.income} />
        <Row label="Расходы" value={totals.expenses} negative />
        <div className="mt-1.5 flex items-center gap-1.5 border-t border-slate-200/50 pt-2.5 text-[11px] font-medium text-slate-400">
          <Info size={12} />
          Себестоимость товаров не учтена
        </div>
      </Card>
    </div>
  )
}
