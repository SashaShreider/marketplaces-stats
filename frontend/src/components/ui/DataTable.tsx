import { useCallback, useEffect, useRef, useState } from 'react'
import { cn } from '@/utils/cn'

/**
 * Колонка таблицы.
 *
 * <p>`group` — надпись группы в двухуровневой шапке. Колонки одной группы стоят
 * рядом, и группа рисуется одной ячейкой на все свои колонки: иначе браузер ставил
 * бы вертикальные линии между подписями одной группы и она распадалась бы.
 *
 * <p>Выравнивание по умолчанию — по правому краю, потому что таблица финансовая и
 * в ней в основном числа. Текстовая колонка (дата) просит `left` явно.
 *
 * <p>`width` — желаемая ширина по умолчанию. Все колонки получают одинаковую:
 * колонка «Дата» и колонка «Прочие» не должны различаться втрое, иначе числа
 * выглядят как случайный набор. Ширину можно тянуть мышью за границу заголовка.
 */
export interface DataColumn<T> {
  /** Ключ колонки: стабильный, не зависит от подписи — по нему считаются colspan и CSV */
  key: string
  label: string
  /** Надпись группы; колонки без группы рисуют пустую верхнюю ячейку */
  group?: string
  align?: 'left' | 'right'
  /** Значение для отрисовки; `null`/`undefined` — пустая ячейка */
  value: (row: T) => React.ReactNode
  /** Значение для CSV. По умолчанию берётся `value`, если это строка или число. */
  csv?: (row: T) => string | number | null | undefined
  /** Класс для ячейки — обычно цвет или начертание, зависящие от значения */
  className?: (row: T) => string | undefined
  /** Заголовок для наведения — например, расшифровка состояния дня */
  title?: (row: T) => string | undefined
  /** Подпись колонки в CSV, если она отличается от `label` */
  csvLabel?: string
}

/**
 * Итоговая строка: значения по колонкам и подпись под ней.
 *
 * <p>`totals` отдельно от `rows`, потому что итог не всегда сумма строк (проценты,
 * доли) и потому что при постраничном выводе он считается по всем строкам, а не по
 * видимым.
 */
export interface DataTableFooter<T> {
  label: string
  value: (column: DataColumn<T>) => React.ReactNode
}

/**
 * Ширина колонки по умолчанию и предел, ниже которого числа перестают помещаться.
 *
 * <p>Минимум подобран под «10 000,00» с минусом: это самое длинное число, которое
 * встречается в дневной таблице. Меньше — и ячейка начнёт обрезать значение,
 * а обрезанное число в финансовом отчёте хуже, чем отсутствующее.
 */
const DEFAULT_COL_W = 98
/** Минимум под «11 918,71» с минусом — самое длинное число в дневной таблице. */
const MIN_COL_W = 82
/** Насколько можно раздвинуть колонку: дальше таблица уезжает за экран целиком. */
const MAX_COL_W = 320

/**
 * Таблица с двухуровневой шапкой, липкой шапкой и липким итогом.
 *
 * <p>Своя, а не библиотечная: нужны липкие шапка и итог при прокрутке и
 * перетаскивание границ колонок, — а любая библиотека приносит с собой собственную
 * модель колонок, фильтры и стили, которые потом приходится переопределять. Здесь
 * таблица рисует то, что ей дали, и ничего больше.
 *
 * <p>Ширины колонок задаются через `colgroup` и `table-fixed`: только так
 * перетаскивание границы меняет ширину ровно у одной колонки, не пересчитывая
 * всю раскладку браузером. За границу группы тянется вся группа — все её колонки
 * получают одинаковую долю прибавки, и пропорции внутри группы сохраняются.
 *
 * @param stickyFirst закрепить первую колонку слева при горизонтальной прокрутке
 * @param maxHeight   высота прокручиваемой области; `null` — без ограничения.
 *                    Число задаёт пиксели, строка — любое CSS-значение (`calc(...)`).
 * @param pageSize    сколько строк показывать; `undefined` — все
 * @param pageLabel   подпись слева от пагинации («Показано 1–90 из 120»)
 */
export default function DataTable<T>({
  rows,
  columns,
  rowKey,
  footer,
  stickyFirst = true,
  maxHeight = 460,
  emptyText = 'Нет данных за период',
  onRowClick,
  pageSize,
  page,
  onPageChange,
}: {
  rows: T[]
  columns: DataColumn<T>[]
  rowKey: (row: T) => string | number
  footer?: DataTableFooter<T>
  stickyFirst?: boolean
  maxHeight?: number | string | null
  emptyText?: string
  onRowClick?: (row: T) => void
  /** Сколько строк на странице; `undefined` — пагинации нет */
  pageSize?: number
  /** Текущая страница с нуля (управляемая пагинация) */
  page?: number
  onPageChange?: (page: number) => void
}) {
  const wrapRef = useRef<HTMLDivElement>(null)
  const [wrapW, setWrapW] = useState(0)

  // Ширина блока нужна, чтобы поделить её между колонками: тринадцать колонок
  // по фиксированной сотне не влезают в ноутбучный экран, и таблица уезжает
  // вправо, хотя место есть.
  useEffect(() => {
    const el = wrapRef.current
    if (!el) return
    const observer = new ResizeObserver((entries) => {
      const w = entries[0].contentRect.width
      if (w > 0) setWrapW(w)
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  /**
   * Ширины, изменённые пользователем. Ключ — `key` колонки.
   *
   * <p>Своё состояние, а не проброс наружу: ширина — свойство просмотра, а не
   * данных, и вызывающему она не нужна.
   */
  const [widths, setWidths] = useState<Record<string, number>>({})
  const dragRef = useRef<{ keys: string[]; startX: number; start: Record<string, number> } | null>(
    null,
  )

  /**
   * Ширина колонки по умолчанию: поровну от доступного места.
   *
   * <p>Не константа, потому что таблиц с разным числом колонок несколько, а окно
   * у пользователя одно. Ниже `MIN_COL_W` не опускаемся — иначе «10 000,00» не
   * помещается; если сумма минимумов больше места, появится горизонтальная
   * прокрутка, и это честнее обрезанных чисел.
   */
  const baseWidth = Math.min(
    MAX_COL_W,
    Math.max(MIN_COL_W, wrapW > 0 ? Math.floor(wrapW / Math.max(1, columns.length)) : DEFAULT_COL_W),
  )

  const widthOf = useCallback(
    (c: DataColumn<T>) => widths[c.key] ?? baseWidth,
    [widths, baseWidth],
  )

  /** Суммарная ширина: по ней задаётся width таблицы, иначе colgroup игнорируется. */
  const totalWidth = columns.reduce((sum, c) => sum + widthOf(c), 0)

  const onDragMove = useCallback((e: MouseEvent) => {
    const drag = dragRef.current
    if (!drag) return
    const delta = e.clientX - drag.startX
    // Прибавка делится между колонками группы поровну: тянуть границу группы —
    // значит менять её ширину целиком, а не растягивать одну колонку за счёт
    // соседних.
    const share = delta / drag.keys.length
    setWidths((prev) => {
      const next = { ...prev }
      for (const key of drag.keys) {
        const base = drag.start[key] ?? baseWidth
        next[key] = Math.round(Math.min(MAX_COL_W, Math.max(MIN_COL_W, base + share)))
      }
      return next
    })
  }, [baseWidth])

  const onDragEnd = useCallback(() => {
    dragRef.current = null
    document.body.style.cursor = ''
    window.removeEventListener('mousemove', onDragMove)
    window.removeEventListener('mouseup', onDragEnd)
  }, [onDragMove])

  const startDrag = (keys: string[], clientX: number) => {
    const start: Record<string, number> = {}
    for (const k of keys) start[k] = widths[k] ?? baseWidth
    dragRef.current = { keys, startX: clientX, start }
    document.body.style.cursor = 'col-resize'
    window.addEventListener('mousemove', onDragMove)
    window.addEventListener('mouseup', onDragEnd)
  }

  // Слушатели висят на window: если отпустить мышь вне заголовка, drag без них
  // не закончился бы и колонка тянулась бы за курсором до перезагрузки.
  useEffect(() => onDragEnd, [onDragEnd])

  /**
   * Группы шапки: подряд идущие колонки с одинаковой подписью.
   *
   * Считается здесь, а не приходит готовым: тогда добавление колонки в группу —
   * одна строка в описании, и шапка не может разойтись с телом таблицы.
   */
  const groups: { label: string; span: number; keys: string[] }[] = []
  for (const c of columns) {
    const label = c.group ?? ''
    const last = groups[groups.length - 1]
    if (last && last.label === label) {
      last.span += 1
      last.keys.push(c.key)
    } else groups.push({ label, span: 1, keys: [c.key] })
  }
  const hasGroups = groups.some((g) => g.label !== '')

  const total = rows.length
  const size = pageSize && pageSize > 0 ? pageSize : total
  const pages = Math.max(1, Math.ceil(total / Math.max(1, size)))
  const current = Math.min(Math.max(0, page ?? 0), pages - 1)
  const visible = pageSize ? rows.slice(current * size, current * size + size) : rows
  const firstShown = total === 0 ? 0 : current * size + 1
  const lastShown = Math.min(total, current * size + size)

  // Линии темнее прежних: на светлом фоне border-slate-100 строки почти
  // растворялись, и глаз перескакивал через строку. Горизонтальные и
  // вертикальные разделители есть и в шапке, и в теле, и в итоге — иначе
  // тринадцать числовых колонок читаются как одна строка цифр.
  //
  // Обе строки шапки одной высоты и с текстом по центру: разная высота и
  // прижатый к краю текст читались как две несвязанные таблицы. Отступы у
  // ячеек нулевые — высоту задаёт только строка, и «воздуха» между строками
  // не остаётся.
  const HEAD_ROW_H = 24

  // Ячейка шапки не обрезает содержимое сама.
  //
  // <p>`overflow-hidden` на липком элементе ломает `position: sticky` в Chromium:
  // шапка перестаёт липнуть и уезжает вместе с данными. Поэтому обрезка с
  // многоточием живёт на вложенном `span`, а сама ячейка остаётся `visible`.
  const thCls =
    'border-b border-r border-slate-300 bg-slate-50 px-2 py-0 align-middle text-[9px] font-bold leading-none text-slate-500 last:border-r-0'
  const thGroupCls =
    'border-b border-r border-slate-300 bg-slate-100 px-2 py-0 align-middle text-[9px] font-bold leading-none text-slate-600 last:border-r-0'
  /** Обрезка подписи: единственное место, где текст может не поместиться. */
  const thTextCls = 'block truncate'
  // Строки тела чуть выше прежнего: 4px отступа сверху и снизу.
  const tdCls =
    'whitespace-nowrap border-b border-r border-slate-200 px-2 py-1 align-middle text-[11px] leading-tight last:border-r-0'

  /** Ручка перетаскивания ширины. */
  const Handle = ({ keys }: { keys: string[] }) => (
    <span
      onMouseDown={(e) => {
        e.preventDefault()
        e.stopPropagation()
        startDrag(keys, e.clientX)
      }}
      className="absolute right-0 top-0 z-30 h-full w-[5px] cursor-col-resize select-none hover:bg-brand-400/40"
      title="Потяните, чтобы изменить ширину"
    />
  )

  return (
    <div className="flex min-h-0 flex-col gap-2">
      <div ref={wrapRef} className="rounded-lg border border-slate-300">
        <div
          // Скругление живёт на самом прокручиваемом блоке, а не на внешнем:
          // `overflow-hidden` снаружи делал бы этот блок единственным
          // «прокручиваемым предком», и шапка липла бы к неподвижной обёртке —
          // то есть уезжала вместе с данными.
          className="scroll-slim overflow-auto rounded-lg"
          style={
            maxHeight === null || maxHeight === undefined
              ? undefined
              : { maxHeight: typeof maxHeight === 'number' ? `${maxHeight}px` : maxHeight }
          }
        >
          <table
            className="table-fixed border-separate border-spacing-0"
            style={{ width: totalWidth, minWidth: '100%' }}
          >
            <colgroup>
              {columns.map((c) => (
                <col key={c.key} style={{ width: widthOf(c) }} />
              ))}
            </colgroup>
            <thead className="sticky top-0 z-20">
              {hasGroups && (
                <tr>
                  {groups.map((g, i) => {
                    // Первая ячейка группы обязана ехать поверх шапки при
                    // горизонтальной прокрутке, иначе её накрывают соседние.
                    const first = groups.slice(0, i).reduce((s, x) => s + x.span, 0) === 0
                    return (
                      <th
                        key={`${g.label}-${i}`}
                        colSpan={g.span}
                        title={g.label || undefined}
                        style={{ height: HEAD_ROW_H, top: 0 }}
                        className={cn(
                          thGroupCls,
                          'relative',
                          first && stickyFirst && 'left-0 z-30',
                          g.label === '' && 'border-b-slate-300',
                        )}
                      >
                        <span className={thTextCls}>{g.label}</span>
                        <Handle keys={g.keys} />
                      </th>
                    )
                  })}
                </tr>
              )}
              <tr>
                {columns.map((c, i) => (
                  <th
                    key={c.key}
                    title={c.label}
                    style={{ height: HEAD_ROW_H, top: hasGroups ? HEAD_ROW_H : 0 }}
                    className={cn(
                      thCls,
                      // Первая колонка липкая по горизонтали: при прокрутке
                      // вправо дата должна оставаться на виду. Вертикаль держит
                      // сам `thead`.
                      i === 0 && stickyFirst ? 'sticky left-0 z-30' : 'relative',
                      (c.align ?? 'right') === 'left' ? 'text-left' : 'text-right',
                    )}
                  >
                    <span className={thTextCls}>{c.label}</span>
                    <Handle keys={[c.key]} />
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {visible.length === 0 && (
                <tr>
                  <td
                    colSpan={columns.length}
                    className="px-3 py-10 text-center text-[11.5px] font-medium text-slate-400"
                  >
                    {emptyText}
                  </td>
                </tr>
              )}
              {visible.map((row) => (
                <tr
                  key={rowKey(row)}
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                  className={cn(
                    'group transition-colors hover:bg-brand-50/50',
                    onRowClick && 'cursor-pointer',
                  )}
                >
                  {columns.map((c, i) => (
                    <td
                      key={c.key}
                      title={c.title?.(row)}
                      className={cn(
                        tdCls,
                        'overflow-hidden text-ellipsis',
                        (c.align ?? 'right') === 'left' ? 'text-left' : 'text-right',
                        // Первая колонка прилипает вместе с фоном: без фона строки
                        // просвечивают сквозь неё при прокрутке.
                        i === 0 && stickyFirst && 'left-0 z-10 bg-white group-hover:bg-brand-50/50',
                        c.className?.(row),
                      )}
                    >
                      {c.value(row)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
            {footer && (
              <tfoot>
                <tr>
                  {columns.map((c, i) => (
                    <td
                      key={c.key}
                      className={cn(
                        'sticky bottom-0 z-20 whitespace-nowrap border-t-2 border-r border-slate-300 bg-slate-50 px-2 py-1 last:border-r-0',
                        (c.align ?? 'right') === 'left' ? 'text-left' : 'text-right',
                        i === 0 && stickyFirst && 'left-0 z-30',
                      )}
                    >
                      {i === 0 && (
                        <span className="text-[11px] font-extrabold text-slate-900">
                          {footer.label}
                        </span>
                      )}
                      {i !== 0 && footer.value(c)}
                    </td>
                  ))}
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      </div>

      {/* Пагинация под таблицей: сколько строк показано и переход по страницам.
          Счёт строк и листалка стоят вместе — по отдельности они отвечают на
          разные вопросы, и человек ищет их в разных местах. */}
      {pageSize && total > 0 && (
        <div className="flex items-center justify-between gap-3 px-0.5">
          <span className="text-[11.5px] font-medium text-slate-400">
            Показано{' '}
            <span className="tnum font-bold text-slate-600">
              {firstShown}–{lastShown}
            </span>{' '}
            из <span className="tnum font-bold text-slate-600">{total}</span>
          </span>
          {pages > 1 && onPageChange && (
            <div className="flex items-center gap-2">
              <button
                disabled={current <= 0}
                onClick={() => onPageChange(current - 1)}
                title="Предыдущая страница"
                className="flex h-6 w-6 items-center justify-center rounded-md border border-slate-200 text-slate-500 transition-all hover:border-brand-300 hover:text-brand-600 disabled:cursor-not-allowed disabled:opacity-35"
              >
                ‹
              </button>
              <span className="text-[11.5px] font-semibold text-slate-500">
                Страница{' '}
                <span className="tnum font-extrabold text-slate-800">{current + 1}</span> из{' '}
                <span className="tnum font-extrabold text-slate-800">{pages}</span>
              </span>
              <button
                disabled={current >= pages - 1}
                onClick={() => onPageChange(current + 1)}
                title="Следующая страница"
                className="flex h-6 w-6 items-center justify-center rounded-md border border-slate-200 text-slate-500 transition-all hover:border-brand-300 hover:text-brand-600 disabled:cursor-not-allowed disabled:opacity-35"
              >
                ›
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
