import { useEffect, useRef, useState, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import {
  Check, CheckCircle2, ChevronDown, Loader2, LogOut, Plus, Trash2, User as UserIcon,
} from 'lucide-react'
import { cn } from '@/utils/cn'
import { MarketplaceLogo } from '@/components/ui'
import type { AccountInfo, User } from '@/types'
import { shopLabel, shopSubLabel } from '@/utils/marketplace'

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

/** Сколько товаров в каталоге и были ли импорты — по этому видно, заведён ли магазин. */
function statusText(a: AccountInfo): string {
  if (a.catalogProducts > 0) {
    return `${a.catalogProducts.toLocaleString('ru-RU')} товаров · ${a.importRuns} импортов`
  }
  return a.importRuns > 0 ? `${a.importRuns} импортов · каталог не загружен` : 'Данных ещё нет'
}

// ─── Переключатель магазина ──────────────────────────────────────────────────

/**
 * Переключатель магазина.
 *
 * <p>Магазины сгруппированы по платформе: у продавца их бывает несколько, и без
 * группировки список быстро превращается в неразличимую колонку одинаковых названий.
 *
 * <p>Рядом с названием показывается clientId, потому что названия у кабинетов одного
 * продавца совпадают, а clientId продавец знает из своего кабинета.
 */
function AccountSelector({
  current,
  list,
  onChange,
}: {
  current: AccountInfo | null
  list: AccountInfo[]
  onChange: (accountId: number) => void
}) {
  const { open, setOpen, ref } = usePopover()

  if (!current) {
    return <div className="h-[38px] w-[120px] animate-pulse-soft rounded-xl bg-slate-100" />
  }

  // Группировка по платформе с сохранением порядка из серверного списка: он уже
  // отсортирован по платформе и порядку подключения.
  const groups = list.reduce<{ code: string; items: AccountInfo[] }[]>((acc, a) => {
    const last = acc[acc.length - 1]
    if (last && last.code === a.marketplaceCode) last.items.push(a)
    else acc.push({ code: a.marketplaceCode, items: [a] })
    return acc
  }, [])

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
        <MarketplaceLogo code={current.marketplaceCode} size="sm" />
        <span className="max-w-[190px] truncate">{shopLabel(current)}</span>
        <ChevronDown size={15} className={cn('text-slate-400 transition-transform', open && 'rotate-180')} />
      </button>

      <AnimatePresence>
        {open && (
          <PopoverPanel className="w-[300px]">
            <div className="max-h-[380px] overflow-y-auto p-1.5">
              {groups.map((group) => (
                <div key={group.code} className="mb-1 last:mb-0">
                  <div className="px-3 pb-1 pt-2 text-[10px] font-bold uppercase tracking-[0.14em] text-slate-400">
                    {group.code}
                    {group.items.length > 1 && (
                      <span className="ml-1.5 font-semibold normal-case tracking-normal text-slate-300">
                        {group.items.length}
                      </span>
                    )}
                  </div>
                  {group.items.map((a) => {
                    const active = a.id === current.id
                    return (
                      <button
                        key={a.id}
                        onClick={() => {
                          onChange(a.id)
                          setOpen(false)
                        }}
                        className={cn(
                          'flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-colors',
                          active ? 'bg-brand-50' : 'hover:bg-slate-50',
                        )}
                      >
                        <MarketplaceLogo code={a.marketplaceCode} size="md" />
                        <span className="min-w-0 flex-1">
                          <span className="flex items-center gap-2 text-[13px] font-bold text-slate-800">
                            <span className="truncate">{shopLabel(a)}</span>
                            {active && <Check size={14} className="shrink-0 text-brand-600" />}
                          </span>
                          <span className="mt-0.5 flex items-center gap-1.5 text-[11px] font-medium text-slate-400">
                            <span className="shrink-0 font-mono">{shopSubLabel(a)}</span>
                            <span className="text-slate-300">·</span>
                            <span className="truncate">{statusText(a)}</span>
                          </span>
                        </span>
                      </button>
                    )
                  })}
                </div>
              ))}
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
  onAdd,
  onEdit,
  onDisconnect,
  onLogout,
}: {
  user: User
  list: AccountInfo[]
  /** null — подключение нового магазина, иначе замена его ключей */
  onAdd: () => void
  onEdit: (account: AccountInfo) => void
  onDisconnect: (accountId: number) => Promise<void>
  onLogout: () => void
}) {
  const { open, setOpen, ref } = usePopover()
  // Подтверждение удаления держим по идентификатору, а не по коду платформы:
  // магазинов на одной площадке несколько, и по коду подтверждение уезжало бы не туда.
  const [confirm, setConfirm] = useState<number | null>(null)
  const [deleting, setDeleting] = useState<number | null>(null)

  useEffect(() => {
    if (!open) setConfirm(null)
  }, [open])

  const doDisconnect = async (accountId: number) => {
    setDeleting(accountId)
    try {
      await onDisconnect(accountId)
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
              <div className="flex items-center justify-between">
                <div className="text-[10.5px] font-bold uppercase tracking-[0.12em] text-slate-400">
                  Мои магазины
                </div>
                <button
                  onClick={() => {
                    setOpen(false)
                    onAdd()
                  }}
                  className="flex items-center gap-1 rounded-lg bg-brand-600 px-2.5 py-1 text-[11px] font-bold text-white shadow-sm shadow-brand-600/30 transition-all hover:bg-brand-700 active:scale-95"
                >
                  <Plus size={12} strokeWidth={3} />
                  Добавить
                </button>
              </div>
              <div className="mt-2.5 space-y-2">
                {list.length === 0 && (
                  <p className="rounded-xl border border-dashed border-slate-200 px-3 py-3 text-center text-[11.5px] font-medium text-slate-400">
                    Пока ни одного магазина не подключено
                  </p>
                )}
                {list.map((a) => (
                  <div
                    key={a.id}
                    className="rounded-xl border border-slate-200/80 px-3 py-2.5"
                  >
                    <div className="flex items-center gap-3">
                      <MarketplaceLogo code={a.marketplaceCode} size="md" />
                      <div className="min-w-0 flex-1">
                        <div className="truncate text-[12.5px] font-bold text-slate-800">
                          {shopLabel(a)}
                        </div>
                        <div className="mt-0.5 flex items-center gap-1.5 text-[11px] font-semibold text-emerald-600">
                          <CheckCircle2 size={12} />
                          <span className="font-mono">{shopSubLabel(a)}</span>
                        </div>
                      </div>
                      {confirm !== a.id && (
                        <div className="flex gap-1">
                          <button
                            onClick={() => {
                              setOpen(false)
                              onEdit(a)
                            }}
                            className="rounded-lg px-2 py-1.5 text-[11px] font-bold text-slate-500 transition-colors hover:bg-slate-100"
                            title="Заменить API-ключ"
                          >
                            Ключ
                          </button>
                          <button
                            onClick={() => setConfirm(a.id)}
                            className="flex h-7 w-7 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-rose-50 hover:text-rose-500"
                            title="Отключить магазин"
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      )}
                    </div>

                    {confirm === a.id && (
                      <div className="mt-2.5 rounded-lg bg-rose-50 p-2.5">
                        <p className="text-[11.5px] font-semibold leading-relaxed text-rose-700">
                          Будут удалены подключение и все загруженные данные этого магазина.
                          Остальные магазины не пострадают.
                        </p>
                        <div className="mt-2 flex justify-end gap-1.5">
                          <button
                            onClick={() => setConfirm(null)}
                            className="rounded-md px-2.5 py-1 text-[11px] font-bold text-slate-500 hover:bg-white"
                          >
                            Отмена
                          </button>
                          <button
                            disabled={deleting === a.id}
                            onClick={() => doDisconnect(a.id)}
                            className="flex items-center gap-1.5 rounded-md bg-rose-600 px-2.5 py-1 text-[11px] font-bold text-white hover:bg-rose-700 disabled:opacity-60"
                          >
                            {deleting === a.id && <Loader2 size={11} className="animate-spin" />}
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
  onAccountChange,
  onEditAccount,
  onDisconnect,
  onLogout,
}: {
  user: User
  list: AccountInfo[]
  current: AccountInfo | null
  onAccountChange: (accountId: number) => void
  /** null — подключение нового магазина */
  onEditAccount: (account: AccountInfo | null) => void
  onDisconnect: (accountId: number) => Promise<void>
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
        <AccountSelector current={current} list={list} onChange={onAccountChange} />
        <span className="hidden h-6 w-px bg-slate-200 sm:block" />
        <ProfileMenu
          user={user}
          list={list}
          onAdd={() => onEditAccount(null)}
          onEdit={onEditAccount}
          onDisconnect={onDisconnect}
          onLogout={onLogout}
        />
      </div>
    </header>
  )
}
