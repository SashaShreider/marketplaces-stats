import { useEffect, useRef, useState, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Check, ChevronDown, Search } from 'lucide-react'
import { cn } from '@/utils/cn'

export interface SelectOption {
  value: string
  label: string
  /** Вторая строка в списке: артикул, SKU или пояснение. */
  hint?: string
}

/**
 * Выпадающий список для строки фильтров.
 *
 * <p>Один компонент на автора и товар: снаружи они отличаются только значениями,
 * а поведение — закрытие по клику мимо, Escape, подсветка выбранного — должно быть
 * одинаковым. Свой список, а не нативный select: у последнего нельзя ни показать
 * артикул второй строкой, ни искать по серверу.
 */
export default function SelectMenu({
  value = '',
  values = [],
  multiple = false,
  options,
  onChange,
  onToggle,
  placeholder,
  valueLabel,
  icon,
  searchable = false,
  searchPlaceholder = 'Поиск…',
  onSearch,
  onOpen,
  loading = false,
  disabled = false,
  className,
  panelClassName,
  emptyText = 'Ничего не найдено',
}: {
  value?: string
  /** Выбранные значения в режиме множественного выбора. */
  values?: string[]
  multiple?: boolean
  options: SelectOption[]
  onChange: (value: string) => void
  /** Переключение одного значения в режиме множественного выбора. */
  onToggle?: (value: string) => void
  placeholder: string
  /** Подпись выбранного значения, если его нет в текущем списке (например, поиск уже сменился). */
  valueLabel?: string
  icon?: ReactNode
  searchable?: boolean
  searchPlaceholder?: string
  /** Поиск на сервере: если задан, список фильтрует не компонент, а вызывающий код. */
  onSearch?: (query: string) => void
  /** Вызывается при открытии — удобно догрузить варианты. */
  onOpen?: () => void
  loading?: boolean
  disabled?: boolean
  className?: string
  panelClassName?: string
  emptyText?: string
}) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const ref = useRef<HTMLDivElement>(null)

  const close = () => {
    setOpen(false)
    setQuery('')
  }

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) close()
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && close()
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const toggle = () => {
    if (disabled) return
    if (open) {
      close()
      return
    }
    setOpen(true)
    onOpen?.()
  }

  const selected = options.find((o) => o.value === value)
  const singleLabel = selected?.label ?? valueLabel ?? placeholder
  // В множественном выборе одна книга ещё читается по названию, а несколько —
  // только числом: перечисление названий не помещается в плашку.
  const label = multiple
    ? values.length === 0
      ? placeholder
      : values.length === 1
        ? (options.find((o) => o.value === values[0])?.label ?? valueLabel ?? placeholder)
        : `Выбрано ${values.length}`
    : singleLabel
  const needle = query.trim().toLowerCase()
  // «Все авторы» / «Все товары» остаются в списке даже при поиске: это не вариант
  // из данных, а способ снять отбор, и искать его текстом бессмысленно.
  const visible = onSearch
    ? options
    : options.filter((o) => o.value === '' || !needle || o.label.toLowerCase().includes(needle))
  const isEmpty = multiple ? values.length === 0 : value === ''

  return (
    <div ref={ref} className={cn('relative', className)}>
      <button
        type="button"
        disabled={disabled}
        onClick={toggle}
        title={label}
        className={cn(
          'flex h-9 w-full items-center gap-2 rounded-xl border bg-white pl-2.5 pr-2 text-[12.5px] font-bold transition-all',
          disabled
            ? 'cursor-not-allowed border-slate-200 bg-slate-50 text-slate-400'
            : open
              ? 'border-brand-300 text-brand-700 shadow-sm ring-4 ring-brand-500/10'
              : isEmpty
                ? 'border-slate-200 text-slate-600 hover:border-slate-300 hover:shadow-sm'
                : 'border-brand-300 bg-brand-50 text-brand-700 hover:border-brand-400',
        )}
      >
        {icon && <span className="shrink-0 text-slate-400">{icon}</span>}
        <span className="min-w-0 flex-1 truncate text-left">{label}</span>
        <ChevronDown
          size={14}
          className={cn('shrink-0 text-slate-400 transition-transform', open && 'rotate-180')}
        />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -5, scale: 0.985 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -5, scale: 0.985 }}
            transition={{ duration: 0.14, ease: 'easeOut' }}
            className={cn(
              'absolute left-0 top-full z-50 mt-1.5 min-w-full origin-top-left overflow-hidden rounded-xl border border-slate-200/80 bg-white shadow-xl shadow-slate-900/10',
              panelClassName,
            )}
          >
            {searchable && (
              <div className="relative border-b border-slate-100 p-2">
                <Search size={13} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300" />
                <input
                  autoFocus
                  value={query}
                  onChange={(e) => {
                    setQuery(e.target.value)
                    onSearch?.(e.target.value)
                  }}
                  placeholder={searchPlaceholder}
                  className="w-full rounded-lg border border-slate-200 bg-slate-50/60 py-1.5 pl-7.5 pr-2.5 text-[12px] font-semibold text-slate-800 outline-none placeholder:font-medium placeholder:text-slate-400 focus:border-brand-400 focus:bg-white"
                />
              </div>
            )}

            <div className="scroll-slim max-h-[290px] overflow-y-auto p-1">
              {loading && (
                <div className="px-3 py-2 text-[11.5px] font-medium text-slate-400">Ищем…</div>
              )}
              {!loading && visible.length === 0 && (
                <div className="px-3 py-2 text-[11.5px] font-medium text-slate-400">{emptyText}</div>
              )}
              {visible.map((o) => {
                const active = multiple
                  ? o.value === ''
                    ? values.length === 0
                    : values.includes(o.value)
                  : o.value === value
                return (
                  <button
                    key={o.value || '__all__'}
                    type="button"
                    onClick={() => {
                      if (multiple) {
                        if (o.value === '') onChange('')
                        else onToggle?.(o.value)
                        return
                      }
                      onChange(o.value)
                      close()
                    }}
                    className={cn(
                      'flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left transition-colors',
                      active ? 'bg-brand-50' : 'hover:bg-slate-50',
                    )}
                  >
                    <span className="min-w-0 flex-1">
                      <span
                        className={cn(
                          'block truncate text-[12.5px] font-semibold',
                          active ? 'text-brand-700' : 'text-slate-700',
                        )}
                      >
                        {o.label}
                      </span>
                      {o.hint && (
                        <span className="tnum mt-0.5 block truncate text-[10.5px] font-medium text-slate-400">
                          {o.hint}
                        </span>
                      )}
                    </span>
                    {active && <Check size={13} strokeWidth={2.8} className="shrink-0 text-brand-600" />}
                  </button>
                )
              })}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
