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
 * Таблица с двухуровневой шапкой, липкой шапкой и липким итогом.
 *
 * <p>Своя, а не библиотечная: нужна ровно одна особенность — липкие шапка и итог при
 * горизонтальной и вертикальной прокрутке, — а любая библиотека приносит с собой
 * собственную модель колонок, сортировку и стили, которые потом приходится
 * переопределять. Здесь таблица рисует то, что ей дали, и ничего больше.
 *
 * <p>Групповая шапка собирается из `group` подряд идущих колонок. Колонки одной
 * группы обязаны стоять рядом: если группа встретится дважды, получится две
 * шапки с одинаковой подписью, и это будет видно сразу.
 *
 * @param stickyFirst закрепить первую колонку слева при горизонтальной прокрутке
 * @param maxHeight   высота прокручиваемой области; `null` — без ограничения.
 *                    Число задаёт пиксели, строка — любое CSS-значение (`calc(...)`).
 *                    Без своей прокрутки липкая шапка не работает: `sticky`
 *                    прилипает к ближайшему прокручиваемому предку, и если таблицу
 *                    прокручивает окно, шапка уезжает вместе с ней.
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
}: {
  rows: T[]
  columns: DataColumn<T>[]
  rowKey: (row: T) => string | number
  footer?: DataTableFooter<T>
  stickyFirst?: boolean
  maxHeight?: number | string | null
  emptyText?: string
  onRowClick?: (row: T) => void
}) {
  /**
   * Группы шапки: подряд идущие колонки с одинаковой подписью.
   *
   * Считается здесь, а не приходит готовым: тогда добавление колонки в группу —
   * одна строка в описании, и шапка не может разойтись с телом таблицы.
   */
  const groups: { label: string; span: number }[] = []
  for (const c of columns) {
    const label = c.group ?? ''
    const last = groups[groups.length - 1]
    if (last && last.label === label) last.span += 1
    else groups.push({ label, span: 1 })
  }
  const hasGroups = groups.some((g) => g.label !== '')

  // Фон шапки непрозрачный: при прокрутке под ней проходят строки, и сквозь
  // полупрозрачный фон читались их числа — шапка выглядела как ещё одна строка.
  const thCls =
    'sticky z-20 whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2.5 py-2 text-[10px] font-extrabold uppercase tracking-[0.06em] text-slate-400'
  const thGroupCls =
    'sticky top-0 z-20 h-7 whitespace-nowrap border-b border-slate-100 bg-slate-100 px-2.5 text-[10px] font-extrabold uppercase tracking-[0.08em] text-slate-500'
  const tdCls = 'whitespace-nowrap border-b border-slate-100 px-2.5 py-1.5'

  return (
    <div className="overflow-hidden rounded-xl border border-slate-200/80">
      <div
        className="scroll-slim overflow-auto"
        style={
          maxHeight === null || maxHeight === undefined
            ? undefined
            : { maxHeight: typeof maxHeight === 'number' ? `${maxHeight}px` : maxHeight }
        }
      >
        <table className="w-full border-separate border-spacing-0">
          <thead>
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
                      className={cn(
                        thGroupCls,
                        first && stickyFirst && 'left-0 z-30',
                        g.label === '' && 'border-b-slate-200',
                      )}
                    >
                      {g.label}
                    </th>
                  )
                })}
              </tr>
            )}
            <tr>
              {columns.map((c, i) => (
                <th
                  key={c.key}
                  className={cn(
                    thCls,
                    // Второй ряд шапки встаёт под первым, а не поверх него: оба
                    // липнут к верху, и без смещения групповой ряд накрывался бы
                    // рядом колонок при вертикальной прокрутке.
                    hasGroups ? 'top-7' : 'top-0',
                    i === 0 && stickyFirst && 'left-0 z-30',
                    (c.align ?? 'right') === 'left' ? 'text-left' : 'text-right',
                  )}
                >
                  {c.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr>
                <td
                  colSpan={columns.length}
                  className="px-3 py-10 text-center text-[12.5px] font-medium text-slate-400"
                >
                  {emptyText}
                </td>
              </tr>
            )}
            {rows.map((row) => (
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
                      'sticky bottom-0 z-20 whitespace-nowrap border-t-2 border-slate-200 bg-slate-50 px-2.5 py-2',
                      (c.align ?? 'right') === 'left' ? 'text-left' : 'text-right',
                      i === 0 && stickyFirst && 'left-0 z-30',
                    )}
                  >
                    {i === 0 && (
                      <span className="text-[12.5px] font-extrabold text-slate-900">
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
  )
}
