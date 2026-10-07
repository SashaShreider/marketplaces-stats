import { BarChart3, Package } from 'lucide-react'
import { cn } from '@/utils/cn'

export type PageId = 'overview' | 'products'

const NAV: { id: PageId; label: string; icon: typeof BarChart3 }[] = [
  { id: 'overview', label: 'Обзор', icon: BarChart3 },
  { id: 'products', label: 'Товары', icon: Package },
]

export default function Sidebar({
  page,
  onNavigate,
}: {
  page: PageId
  onNavigate: (p: PageId) => void
}) {
  return (
    <aside className="sticky top-0 hidden h-screen w-[232px] shrink-0 flex-col border-r border-slate-200/70 bg-white/70 backdrop-blur-xl lg:flex">
      {/* Логотип */}
      <div className="flex items-center gap-2.5 px-5 pb-6 pt-5">
        <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-brand-500 to-brand-700 shadow-lg shadow-brand-500/30">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path
              d="M4 18V7l5.5 7L12 9.5 14.5 14 20 6v12"
              stroke="white"
              strokeWidth="2.4"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </span>
        <div className="leading-tight">
          <div className="text-[15px] font-extrabold tracking-tight text-slate-900">
            Market<span className="text-brand-600">Analytics</span>
          </div>
          <div className="text-[10px] font-medium text-slate-400">аналитика продаж</div>
        </div>
      </div>

      {/* Навигация */}
      <nav className="flex flex-col gap-1 px-3">
        <div className="px-3 pb-1.5 text-[10px] font-bold uppercase tracking-[0.14em] text-slate-400">
          Аналитика
        </div>
        {NAV.map(({ id, label, icon: Icon }) => {
          const active = page === id
          return (
            <button
              key={id}
              onClick={() => onNavigate(id)}
              className={cn(
                'group relative flex items-center gap-3 rounded-xl px-3.5 py-2.5 text-[13.5px] font-semibold transition-all duration-200',
                active
                  ? 'bg-brand-50 text-brand-700'
                  : 'text-slate-500 hover:bg-slate-100/80 hover:text-slate-800',
              )}
            >
              {active && (
                <span className="absolute left-0 top-1/2 h-5 w-[3px] -translate-y-1/2 rounded-r-full bg-brand-600" />
              )}
              <Icon
                size={18}
                strokeWidth={active ? 2.4 : 2}
                className={cn(
                  'transition-colors',
                  active ? 'text-brand-600' : 'text-slate-400 group-hover:text-slate-600',
                )}
              />
              {label}
            </button>
          )
        })}
      </nav>

      {/*  <div className="mt-auto px-3 pb-5">
        <div className="rounded-2xl border border-slate-200/70 bg-gradient-to-br from-slate-50 to-brand-50/60 p-4">
          <div className="text-[12px] font-bold text-slate-800">Нужна помощь?</div>
          <p className="mt-1 text-[11px] leading-relaxed text-slate-500">
            Инструкции по подключению API-ключей — в разделе профиля.
          </p>
        </div>
      </div> **/}

    </aside>
  )
}
