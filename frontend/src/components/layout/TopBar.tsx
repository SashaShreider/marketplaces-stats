import { useEffect, useRef, useState, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import {
  Check, CheckCircle2, ChevronDown, CircleAlert, Loader2, LogOut, Plus, Trash2, User as UserIcon,
} from 'lucide-react'
import { cn } from '@/utils/cn'
import { MarketplaceLogo } from '@/components/ui'
import type { MarketplaceInfo, User } from '@/types'

// ─── Универсальный поповер ───────────────────────────────────────────────────

function usePopover() {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])
  return { open, setOpen, ref }
}

function PopoverPanel({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: -6, scale: 0.98 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -6, scale: 0.98 }}
      transition={{ duration: 0.16, ease: 'easeOut' }}
      className={cn(
        'absolute right-0 top-full z-50 mt-2 max-w-[calc(100vw-1.5rem)] origin-top-right overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-xl shadow-slate-900/8',
        className,
      )}
    >
      {children}
    </motion.div>
  )
}

function statusText(m: MarketplaceInfo): string {
  if (!m.connected) return 'Не подключён'
  return `Подключён${m.accountName ? ` · ${m.accountName}` : m.clientId ? ` · ${m.clientId}` : ''}`
}

function StatusDot({ connected }: { connected: boolean }) {
  return connected ? (
    <span className="h-2 w-2 shrink-0 rounded-full bg-emerald-500 shadow-[0_0_0_3px_rgba(16,185,129,0.18)]" />
  ) : (
    <span className="h-2 w-2 shrink-0 rounded-full bg-slate-300 shadow-[0_0_0_3px_rgba(148,163,184,0.2)]" />
  )
}

// ─── Переключатель маркетплейса ─────────────────────────────────────────────

function MarketplaceSelector({
  current,
  list,
  onChange,
}: {
  current: MarketplaceInfo | null
  list: MarketplaceInfo[]
  onChange: (code: string) => void
}) {
  const { open, setOpen, ref } = usePopover()

  if (!current) {
    return <div className="h-[38px] w-[120px] animate-pulse-soft rounded-xl bg-slate-100" />
  }

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(!open)}
        className={cn(
          'flex items-center gap-2 rounded-xl border py-2 pl-2.5 pr-3 text-[13px] font-bold transition-all',
          open
            ? 'border-brand-300 bg-brand-50 text-brand-700 shadow-sm'
            : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:shadow-sm',
        )}
      >
        <MarketplaceLogo code={current.code} size="sm" />
        {current.name}
        <ChevronDown size={15} className={cn('text-slate-400 transition-transform', open && 'rotate-180')} />
      </button>

      <AnimatePresence>
        {open && (
          <PopoverPanel className="w-[272px]">
            <div className="p-1.5">
              {list.map((m) => {
                const active = m.code === current.code
                return (
                  <button
                    key={m.code}
                    onClick={() => {
                      onChange(m.code)
                      setOpen(false)
                    }}
                    className={cn(
                      'flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-colors',
                      active ? 'bg-brand-50' : 'hover:bg-slate-50',
                    )}
                  >
                    <MarketplaceLogo code={m.code} size="md" />
                    <span className="min-w-0 flex-1">
                      <span className="flex items-center gap-2 text-[13px] font-bold text-slate-800">
                        {m.name}
                        {active && <Check size={14} className="text-brand-600" />}
                      </span>
                      <span className="mt-0.5 flex items-center gap-1.5 text-[11px] font-medium text-slate-400">
                        <StatusDot connected={m.connected} />
                        <span className="truncate">{statusText(m)}</span>
                      </span>
                    </span>
                  </button>
                )
              })}
            </div>
          </PopoverPanel>
        )}
      </AnimatePresence>
    </div>
  )
}

// ─── Профиль пользователя ────────────────────────────────────────────────────

function initials(u: User): string {
  const src = (u.displayName || u.login).trim()
  const parts = src.split(/\s+/)
  return (parts.length > 1 ? parts[0][0] + parts[1][0] : src.slice(0, 2)).toUpperCase()
}

function ProfileMenu({
  user,
  list,
  onConnect,
  onDisconnect,
  onLogout,
}: {
  user: User
  list: MarketplaceInfo[]
  onConnect: (code: string) => void
  onDisconnect: (code: string) => Promise<void>
  onLogout: () => void
}) {
  const { open, setOpen, ref } = usePopover()
  const [confirm, setConfirm] = useState<string | null>(null)
  const [deleting, setDeleting] = useState<string | null>(null)

  useEffect(() => {
    if (!open) setConfirm(null)
  }, [open])

  const doDisconnect = async (code: string) => {
    setDeleting(code)
    try {
      await onDisconnect(code)
    } finally {
      setDeleting(null)
      setConfirm(null)
    }
  }

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(!open)}
        className={cn(
          'flex items-center gap-2.5 rounded-xl border py-1.5 pl-1.5 pr-3 transition-all',
          open
            ? 'border-brand-300 bg-brand-50 shadow-sm'
            : 'border-slate-200 bg-white hover:border-slate-300 hover:shadow-sm',
        )}
      >
        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-gradient-to-br from-slate-600 to-slate-800 text-white">
          <UserIcon size={14} />
        </span>
        <span className="hidden max-w-[130px] truncate text-[13px] font-bold text-slate-700 sm:block">
          {user.displayName || user.login}
        </span>
        <ChevronDown size={15} className={cn('text-slate-400 transition-transform', open && 'rotate-180')} />
      </button>

      <AnimatePresence>
        {open && (
          <PopoverPanel className="w-[350px]">
            <div className="flex items-center gap-3 border-b border-slate-100 bg-slate-50/60 px-4 py-3.5">
              <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-slate-600 to-slate-800 text-[13px] font-extrabold text-white">
                {initials(user)}
              </span>
              <div className="min-w-0">
                <div className="truncate text-[13.5px] font-bold text-slate-900">
                  {user.displayName || user.login}
                </div>
                <div className="truncate text-[11.5px] font-medium text-slate-400">@{user.login}</div>
              </div>
            </div>

            <div className="px-4 pb-2 pt-3">
              <div className="text-[10.5px] font-bold uppercase tracking-[0.12em] text-slate-400">
                Мои маркетплейсы
              </div>
              <div className="mt-2.5 space-y-2">
                {list.map((m) => (
                  <div
                    key={m.code}
                    className="rounded-xl border border-slate-200/80 px-3 py-2.5"
                  >
                    <div className="flex items-center gap-3">
                      <MarketplaceLogo code={m.code} size="md" />
                      <div className="min-w-0 flex-1">
                        <div className="text-[12.5px] font-bold text-slate-800">{m.name}</div>
                        <div
                          className={cn(
                            'mt-0.5 flex items-center gap-1.5 text-[11px] font-semibold',
                            m.connected ? 'text-emerald-600' : 'text-slate-400',
                          )}
                        >
                          {m.connected ? <CheckCircle2 size={12} /> : <CircleAlert size={12} />}
                          <span className="truncate">{statusText(m)}</span>
                        </div>
                      </div>
                      {!m.connected ? (
                        <button
                          onClick={() => {
                            setOpen(false)
                            onConnect(m.code)
                          }}
                          className="flex items-center gap-1 rounded-lg bg-brand-600 px-2.5 py-1.5 text-[11px] font-bold text-white shadow-sm shadow-brand-600/30 transition-all hover:bg-brand-700 active:scale-95"
                        >
                          <Plus size={12} strokeWidth={3} />
                          Подключить
                        </button>
                      ) : (
                        confirm !== m.code && (
                          <div className="flex gap-1">
                            <button
                              onClick={() => {
                                setOpen(false)
                                onConnect(m.code)
                              }}
                              className="rounded-lg px-2 py-1.5 text-[11px] font-bold text-slate-500 transition-colors hover:bg-slate-100"
                              title="Заменить API-ключ"
                            >
                              Ключ
                            </button>
                            <button
                              onClick={() => setConfirm(m.code)}
                              className="flex h-7 w-7 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-rose-50 hover:text-rose-500"
                              title="Отключить магазин"
                            >
                              <Trash2 size={14} />
                            </button>
                          </div>
                        )
                      )}
                    </div>

                    {confirm === m.code && (
                      <div className="mt-2.5 rounded-lg bg-rose-50 p-2.5">
                        <p className="text-[11.5px] font-semibold leading-relaxed text-rose-700">
                          Будут удалены подключение и все загруженные данные магазина. Восстановить
                          можно только повторной загрузкой (она тратит квоту маркетплейса).
                        </p>
                        <div className="mt-2 flex justify-end gap-1.5">
                          <button
                            onClick={() => setConfirm(null)}
                            className="rounded-md px-2.5 py-1 text-[11px] font-bold text-slate-500 hover:bg-white"
                          >
                            Отмена
                          </button>
                          <button
                            disabled={deleting === m.code}
                            onClick={() => doDisconnect(m.code)}
                            className="flex items-center gap-1.5 rounded-md bg-rose-600 px-2.5 py-1 text-[11px] font-bold text-white hover:bg-rose-700 disabled:opacity-60"
                          >
                            {deleting === m.code && <Loader2 size={11} className="animate-spin" />}
                            Удалить
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>

            <div className="mt-1 border-t border-slate-100 p-1.5">
              <button
                onClick={onLogout}
                className="flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-[12.5px] font-semibold text-slate-500 transition-colors hover:bg-slate-50 hover:text-slate-700"
              >
                <LogOut size={14} />
                Выйти из аккаунта
              </button>
            </div>
          </PopoverPanel>
        )}
      </AnimatePresence>
    </div>
  )
}

// ─── Шапка ───────────────────────────────────────────────────────────────────

export default function TopBar({
  user,
  list,
  current,
  onMarketplaceChange,
  onConnect,
  onDisconnect,
  onLogout,
}: {
  user: User
  list: MarketplaceInfo[]
  current: MarketplaceInfo | null
  onMarketplaceChange: (code: string) => void
  onConnect: (code: string) => void
  onDisconnect: (code: string) => Promise<void>
  onLogout: () => void
}) {
  return (
    <header className="sticky top-0 z-40 flex h-16 items-center justify-between gap-3 border-b border-slate-200/70 bg-white/75 px-4 backdrop-blur-xl sm:px-6">
      <div className="flex items-center gap-2 lg:hidden">
        <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-brand-500 to-brand-700">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
            <path d="M4 18V7l5.5 7L12 9.5 14.5 14 20 6v12" stroke="white" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </span>
        <span className="hidden text-[14px] font-extrabold tracking-tight text-slate-900 min-[420px]:block">
          Market<span className="text-brand-600">Analytics</span>
        </span>
      </div>
      <div className="hidden lg:block" />

      <div className="flex items-center gap-2.5 sm:gap-3">
        <MarketplaceSelector current={current} list={list} onChange={onMarketplaceChange} />
        <span className="hidden h-6 w-px bg-slate-200 sm:block" />
        <ProfileMenu
          user={user}
          list={list}
          onConnect={onConnect}
          onDisconnect={onDisconnect}
          onLogout={onLogout}
        />
      </div>
    </header>
  )
}
