import { useEffect, useState } from 'react'
import { BookOpen, UserRound } from 'lucide-react'
import { getAuthors, getProductOptions } from '@/api/client'
import SelectMenu, { type SelectOption } from '@/components/common/SelectMenu'
import type { ProductOption } from '@/types'

/**
 * Отбор отчёта по автору или конкретным книгам.
 *
 * <p>Книг можно выбрать несколько: вопрос «как продаются эти три книги» — обычный,
 * а не редкий. Автор при этом снимается: выбранная книга уже отвечает, чья она, и
 * два отбора одновременно только запутали бы подпись отчёта.
 */
export default function OverviewFilters({
  accountId,
  catalogProducts,
  author,
  skus,
  onAuthorChange,
  onSkusChange,
}: {
  accountId: number
  /** Сколько товаров в каталоге: без него списки пусты, и это надо показать. */
  catalogProducts: number
  author: string
  skus: number[]
  onAuthorChange: (author: string) => void
  onSkusChange: (skus: number[]) => void
}) {
  const [authors, setAuthors] = useState<string[]>([])
  const [books, setBooks] = useState<ProductOption[]>([])
  const [bookQuery, setBookQuery] = useState('')
  const [booksLoading, setBooksLoading] = useState(false)
  // Подписи выбранных книг: после нового поиска сервер может их уже не вернуть,
  // а в свёрнутой плашке название единственной выбранной книги должно остаться.
  const [picked, setPicked] = useState<ProductOption[]>([])

  const disabled = catalogProducts === 0
  const hasScope = author !== '' || skus.length > 0

  // Авторы — те же строки, по которым фильтрует сервер, поэтому выбранное значение
  // гарантированно найдёт товары.
  useEffect(() => {
    let cancelled = false
    getAuthors(accountId)
      .then((list) => !cancelled && setAuthors(list))
      .catch(() => !cancelled && setAuthors([]))
    return () => {
      cancelled = true
    }
  }, [accountId, catalogProducts])

  // Поиск книг на сервере: в каталоге их может быть больше, чем разумно держать
  // одним списком, а на каждый символ запрос отправлять не нужно.
  useEffect(() => {
    if (disabled) {
      setBooks([])
      return
    }
    let cancelled = false
    setBooksLoading(true)
    const timer = window.setTimeout(
      () => {
        getProductOptions(accountId, bookQuery.trim(), 30)
          .then((list) => !cancelled && setBooks(list))
          .catch(() => !cancelled && setBooks([]))
          .finally(() => !cancelled && setBooksLoading(false))
      },
      bookQuery ? 220 : 0,
    )
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [accountId, bookQuery, catalogProducts, disabled])

  // Отбор сняли снаружи — подписи выбранных книг больше не нужны.
  useEffect(() => {
    setPicked((prev) => prev.filter((b) => skus.includes(b.sku)))
    if (skus.length === 0) setPicked([])
  }, [skus])

  const authorOptions: SelectOption[] = [
    { value: '', label: 'Все авторы' },
    ...authors.map((a) => ({ value: a, label: a })),
  ]

  const bookOptions: SelectOption[] = [
    { value: '', label: 'Все товары' },
    ...books.map((b) => ({
      value: String(b.sku),
      label: b.name,
      hint: b.offerId ? `SKU ${b.sku} · ${b.offerId}` : `SKU ${b.sku}`,
    })),
  ]

  const toggleBook = (value: string) => {
    const number = Number(value)
    const exists = skus.includes(number)
    const next = exists ? skus.filter((s) => s !== number) : [...skus, number]
    onSkusChange(next)
    if (exists) {
      setPicked((prev) => prev.filter((b) => b.sku !== number))
    } else {
      const book = books.find((b) => b.sku === number)
      if (book) setPicked((prev) => [...prev.filter((b) => b.sku !== number), book])
      if (author) onAuthorChange('')
    }
  }

  const reset = () => {
    onAuthorChange('')
    onSkusChange([])
    setPicked([])
  }

  const singleLabel =
    skus.length === 1 ? picked.find((b) => b.sku === skus[0])?.name : undefined

  return (
    <>
      <SelectMenu
        value={author}
        options={authorOptions}
        valueLabel={author || undefined}
        placeholder={disabled ? 'Каталог не загружен' : 'Все авторы'}
        icon={<UserRound size={14} />}
        searchable
        searchPlaceholder="Найти автора"
        emptyText="Авторы не найдены"
        onChange={(value) => {
          onAuthorChange(value)
          if (value) {
            onSkusChange([])
            setPicked([])
          }
        }}
        disabled={disabled}
        className="w-[150px] sm:w-[170px]"
        panelClassName="w-[250px]"
      />

      <SelectMenu
        multiple
        value=""
        values={skus.map(String)}
        options={bookOptions}
        valueLabel={singleLabel}
        placeholder={disabled ? 'Каталог не загружен' : 'Все товары'}
        icon={<BookOpen size={14} />}
        searchable
        searchPlaceholder="Название, артикул или ISBN"
        onSearch={setBookQuery}
        onOpen={() => setBookQuery('')}
        loading={booksLoading}
        emptyText="Книги не найдены"
        onChange={() => {
          // «Все товары» в списке снимает отбор целиком.
          onSkusChange([])
          setPicked([])
        }}
        onToggle={toggleBook}
        disabled={disabled}
        className="w-[180px] sm:w-[220px]"
        panelClassName="w-[360px] max-w-[calc(100vw-1.5rem)]"
      />

      {hasScope && (
        <button
          type="button"
          onClick={reset}
          className="text-[11.5px] font-bold text-slate-400 underline decoration-slate-300 underline-offset-2 transition-colors hover:text-slate-600"
        >
          Сбросить
        </button>
      )}
    </>
  )
}
