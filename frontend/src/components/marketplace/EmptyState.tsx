import { motion } from 'framer-motion'
import { KeyRound, PlugZap } from 'lucide-react'
import type { MarketplaceInfo } from '@/types'
import { MarketplaceLogo } from '@/components/ui'

export default function EmptyState({
  mp,
  onConnect,
}: {
  mp: MarketplaceInfo
  onConnect: () => void
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
      className="relative overflow-hidden rounded-2xl border border-slate-200/70 bg-white shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
    >
      <div className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full bg-brand-100/60 blur-3xl" />
      <div className="pointer-events-none absolute -bottom-24 -left-16 h-64 w-64 rounded-full bg-indigo-100/50 blur-3xl" />

      <div className="relative flex flex-col items-center px-6 py-16 text-center sm:py-20">
        <motion.span
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          transition={{ type: 'spring', stiffness: 260, damping: 16, delay: 0.1 }}
          className="relative flex h-20 w-20 items-center justify-center rounded-3xl bg-slate-50 ring-1 ring-slate-200/70"
        >
          <PlugZap size={34} className="text-slate-400" />
          <span className="absolute -bottom-2 -right-2 rounded-xl bg-white p-1 shadow-md ring-1 ring-slate-100">
            <MarketplaceLogo code={mp.code} size="md" />
          </span>
        </motion.span>

        <h3 className="mt-6 text-[19px] font-extrabold tracking-tight text-slate-900">
          {mp.name} не подключён
        </h3>
        <p className="mt-2 max-w-[420px] text-[13px] leading-relaxed text-slate-500">
          Подключите кабинет {mp.name} по API-ключу — после этого можно загрузить продажи, расходы и
          прибыль за любой период.
        </p>

        <button
          onClick={onConnect}
          className="mt-6 flex items-center gap-2 rounded-xl bg-brand-600 px-5 py-3 text-[13.5px] font-bold text-white shadow-lg shadow-brand-600/30 transition-all hover:bg-brand-700 active:scale-95"
        >
          <KeyRound size={16} />
          Подключить {mp.name}
        </button>

        <span className="mt-4 text-[11.5px] font-medium text-slate-400">
          Ключи проверяются запросом к маркетплейсу · данные только на чтение
        </span>
      </div>
    </motion.div>
  )
}
