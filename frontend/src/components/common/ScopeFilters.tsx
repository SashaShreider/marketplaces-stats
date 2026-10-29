import { useEffect, useState } from 'react'
import { BookOpen, UserRound } from 'lucide-react'
import { getAuthors, getProductOptions } from '@/api/client'
import SelectMenu, { type SelectOption } from '@/components/common/SelectMenu'
import type { ProductOption } from '@/types'

/**
 * Отбор отчёта по автору или конкретным товарам — общий для «Обзора» и «Товаров».
 *
 * <p>Один компонент на две страницы, потому что вопрос у них один: «про кого этот
 * отчёт». Раньше на «Товарах» стояли поле поиска и текстовое поле автора, а на
 * «Обзоре» — списки. Два разных способа задать одно и то же заставляли продавца
 * помнить, где искать, и один из них не умел выбирать несколько книг.
 *
 * <p>Книг можно выбрать несколько: вопрос «как продаются эти три книги» — обычный,
 * а не редкий. Автор при этом снимается: выбранная книга уже отвечает, чья она, и
 * два отбора одновременно только запутали бы подпись отчёта.
 *
 * <p>Названия выбранных книг компонент восстанавливает сам: в адресе и в состоянии
 * живут только номера, а подпись «SKU 123» ничего не говорит продавцу.
 */
export default function ScopeFilters({
  accountId,
  catalogProducts,
  author,
  skus,
  onAuthorChange,
  onSkusChange,
  onBooksChange,
}: {
  accountId: number
  /** Сколько товаров в каталоге: без него списки пусты, и это надо показать. */
  catalogProducts: number
  author: string
  skus: number[]
  onAuthorChange: (author: string) => void
  onSkusChange: (skus: number[]) => void
  /**
   * Названия выбранных книг — для подписи отбора снаружи компонента.
   *
   * <p>Необязателен: странице товаров названия не нужны, она не подписывает
   * отбором ничего, кроме самой таблицы.
   */
  onBooksChange?: (books: ProductOption[]) => void
}) {
  const [authors, setAuthors] = useState<string[]>([])
  const [books, setBooks] = useState<ProductOption[]>([])
  const [picked, setPicked] = useState<ProductOption[]>([])
  const [bookQuery, setBookQuery] = useState('')
  const [booksLoading, setBooksLoading] = useState(false)

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

  /**
   * Названия выбранных книг по их номерам.
   *
   * <p>Нужно после перезагрузки: номера пришли из адреса, а названий там нет —
   * держать в ссылке весь каталог незачем. Запрос идёт, только когда чего-то не
   * хватает, иначе каждая правка отбора дёргала бы каталог впустую.
   */
  useEffect(() => {
    if (skus.length === 0) {
      setPicked((prev) => (prev.length > 0 ? [] : prev))
      return
    }
    const known = new Set(picked.map((b) => b.sku))
    if (skus.every((s) => known.has(s))) return
    let cancelled = false
    getProductOptions(accountId, '', 50)
      .then((list) => {
        if (cancelled) return
        setPicked((prev) => {
          const have = new Set(prev.map((b) => b.sku))
          const found = list.filter((b) => !have.has(b.sku) && skus.includes(b.sku))
          return found.length > 0 ? [...prev, ...found] : prev
        })
      })
      .catch(() => {
        /* подписи не будет — на отчёт это не влияет */
      })
    return () => {
      cancelled = true
    }
  }, [accountId, skus, picked])

  // Имена нужны и снаружи: подпись отбора уходит в шапку таблицы, а она живёт на
  // странице. Отдаём их только при изменении — иначе родитель перерисовывался бы
  // на каждый рендер ребёнка.
  useEffect(() => {
    onBooksChange?.(picked)
  }, [picked, onBooksChange])

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
    onSkusChange(exists ? skus.filter((s) => s !== number) : [...skus, number])
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

  const singleLabel = skus.length === 1 ? picked.find((b) => b.sku === skus[0])?.name : undefined

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
