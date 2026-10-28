import { useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Check, Search, User, X } from 'lucide-react'
import { getAuthors, getProductOptions } from '@/api/client'
import type { ProductOption } from '@/types'
import { cn } from '@/utils/cn'

/**
 * Отбор отчёта: автор или конкретные товары.
 *
 * <p>Два отбора рядом, но не вместе: выбрав товар, человек уже ответил, чей он, и
 * складывать его с автором значило бы спрашивать дважды. Поэтому выбор товара
 * сбрасывает автора и наоборот, а подпись отбора в отчёте приходит с сервера
 * (`filter`) — по ней видно, что применено на самом деле.
 *
 * <p>Пустой отбор — это не «ничего не найдено», а весь магазин: и отчёт, и карточки
 * в этом случае считаются по всем товарам.
 */
export default function OverviewFilters({
  accountId,
  author,
  skus,
  onAuthorChange,
  onSkusChange,
  catalogProducts,
}: {
  accountId: number
  author: string
  skus: number[]
  onAuthorChange: (author: string) => void
  onSkusChange: (skus: number[]) => void
  /** Сколько товаров в каталоге — подсказка нужна только когда он загружен */
  catalogProducts: number
}) {
  const [authors, setAuthors] = useState<string[]>([])
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [options, setOptions] = useState<ProductOption[]>([])
  const [loading, setLoading] = useState(false)
  const [chosen, setChosen] = useState<ProductOption[]>([])
  const box = useRef<HTMLDivElement>(null)

  // Подсказки авторов берём из каталога: это те же строки, по которым идёт фильтр,
  // поэтому выбранное значение гарантированно что-то найдёт.
  useEffect(() => {
    let cancelled = false
    getAuthors(accountId)
      .then((list) => !cancelled && setAuthors(list))
      .catch(() => !cancelled && setAuthors([]))
    return () => {
      cancelled = true
    }
  }, [accountId, catalogProducts])

  // Поиск товаров с задержкой: запрос на каждый символ — это десяток запросов на
  // слово, а ответ всё равно нужен только тогда, когда человек перестал печатать.
  useEffect(() => {
    if (!open) return
    let cancelled = false
    setLoading(true)
    const timer = window.setTimeout(() => {
      getProductOptions(accountId, query.trim())
        .then((list) => !cancelled && setOptions(list))
        .catch(() => !cancelled && setOptions([]))
        .finally(() => !cancelled && setLoading(false))
    }, 220)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [accountId, query, open])

  // Клик мимо списка закрывает его: иначе открытый список висит поверх таблицы.
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [open])

  const toggle = (option: ProductOption) => {
    const exists = skus.includes(option.sku)
    const nextSkus = exists ? skus.filter((s) => s !== option.sku) : [...skus, option.sku]
    setChosen((prev) =>
      exists ? prev.filter((p) => p.sku !== option.sku) : [...prev, option],
    )
    // Товар сильнее автора: выбрав товар, человек уже сказал, чей он.
    if (!exists && author) onAuthorChange('')
    onSkusChange(nextSkus)
  }

  const reset = () => {
    setChosen([])
    setQuery('')
    onSkusChange([])
    onAuthorChange('')
  }

  const active = author !== '' || skus.length > 0
  const disabled = catalogProducts === 0

  return (
    <div className="flex flex-wrap items-center gap-2">
      {/* Автор */}
      <div className="relative w-[190px]">
        <User size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-300" />
        <input
          list="overview-authors"
          value={author}
          disabled={disabled}
          onChange={(e) => {
            // Выбор автора снимает отбор по товарам — см. комментарий к компоненту.
            if (skus.length) {
              setChosen([])
              onSkusChange([])
            }
            onAuthorChange(e.target.value)
          }}
          placeholder={disabled ? 'Каталог не загружен' : 'Все авторы'}
          title="Точное совпадение с тем, как автор указан в карточке товара. Начните вводить, чтобы выбрать из списка."
          className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2 pl-8.5 pr-8 text-[12.5px] font-semibold text-slate-800 outline-none transition-all placeholder:font-medium placeholder:text-slate-400 focus:border-brand-400 focus:bg-white focus:ring-4 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:bg-slate-100/60"
        />
        {author && (
          <button
            onClick={() => onAuthorChange('')}
            title="Сбросить автора"
            className="absolute right-2 top-1/2 flex h-5 w-5 -translate-y-1/2 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100"
          >
            <X size={12} />
          </button>
        )}
        <datalist id="overview-authors">
          {authors.map((a) => (
            <option key={a} value={a} />
          ))}
        </datalist>
      </div>

      {/* Товары */}
      <div ref={box} className="relative">
        <button
          disabled={disabled}
          onClick={() => setOpen((v) => !v)}
          title="Ограничить отчёт выбранными товарами"
          className={cn(
            'flex items-center gap-1.5 rounded-xl border px-3 py-2 text-[12.5px] font-bold transition-all disabled:cursor-not-allowed disabled:opacity-50',
            skus.length
              ? 'border-brand-300 bg-brand-50 text-brand-700'
              : 'border-slate-200 bg-slate-50/50 text-slate-600 hover:border-slate-300',
          )}
        >
          Товары
          {skus.length > 0 && (
            <span className="tnum rounded-full bg-brand-600 px-1.5 py-0.5 text-[10.5px] font-extrabold text-white">
              {skus.length}
            </span>
          )}
        </button>

        <AnimatePresence>
          {open && (
            <motion.div
              initial={{ opacity: 0, y: -4 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -4 }}
              transition={{ duration: 0.14 }}
              className="absolute right-0 z-40 mt-1.5 w-[340px] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl shadow-slate-900/10"
            >
              <div className="relative border-b border-slate-100 p-2">
                <Search size={13} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300" />
                <input
                  autoFocus
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Название, артикул или ISBN"
                  className="w-full rounded-lg border border-slate-200 bg-slate-50/60 py-1.5 pl-7.5 pr-2.5 text-[12px] font-semibold text-slate-800 outline-none placeholder:font-medium placeholder:text-slate-400 focus:border-brand-400 focus:bg-white"
                />
              </div>

              <div className="scroll-slim max-h-[260px] overflow-auto py-1">
                {loading && options.length === 0 && (
                  <div className="px-3 py-2 text-[11.5px] font-medium text-slate-400">Ищем…</div>
                )}
                {!loading && options.length === 0 && (
                  <div className="px-3 py-2 text-[11.5px] font-medium text-slate-400">
                    Ничего не нашлось
                  </div>
                )}
                {options.map((o) => {
                  const picked = skus.includes(o.sku)
                  return (
                    <button
                      key={o.sku}
                      onClick={() => toggle(o)}
                      className="flex w-full items-start gap-2 px-3 py-1.5 text-left transition-colors hover:bg-brand-50/70"
                    >
                      <span
                        className={cn(
                          'mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded border',
                          picked
                            ? 'border-brand-600 bg-brand-600 text-white'
                            : 'border-slate-300 bg-white',
                        )}
                      >
                        {picked && <Check size={11} strokeWidth={3.5} />}
                      </span>
                      <span className="min-w-0">
                        <span className="block truncate text-[12px] font-semibold text-slate-700">
                          {o.name}
                        </span>
                        <span className="tnum block text-[10.5px] font-medium text-slate-400">
                          SKU {o.sku}
                          {o.offerId && <> · {o.offerId}</>}
                        </span>
                      </span>
                    </button>
                  )
                })}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Выбранные товары — отдельными метками: в свёрнутом списке их не видно, а
          отчёт уже посчитан по ним. */}
      {chosen.length > 0 && (
        <div className="flex max-w-full flex-wrap items-center gap-1.5">
          {chosen.map((c) => (
            <span
              key={c.sku}
              title={c.name}
              className="flex max-w-[220px] items-center gap-1 rounded-full bg-brand-50 py-1 pl-2.5 pr-1 text-[11.5px] font-bold text-brand-700 ring-1 ring-brand-200/70"
            >
              <span className="truncate">{c.name}</span>
              <button
                onClick={() => toggle(c)}
                title="Убрать товар из отбора"
                className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-brand-500 hover:bg-brand-100"
              >
                <X size={11} />
              </button>
            </span>
          ))}
        </div>
      )}

      {active && (
        <button
          onClick={reset}
          className="text-[11.5px] font-bold text-slate-400 underline decoration-slate-300 underline-offset-2 transition-colors hover:text-slate-600"
        >
          Сбросить отбор
        </button>
      )}
    </div>
  )
}
