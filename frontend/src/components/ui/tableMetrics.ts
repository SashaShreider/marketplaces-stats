/**
 * Размеры таблицы: текст и высоты строк.
 *
 * <p>Вынесены из `DataTable` и колонок в одно место, потому что задавались в трёх:
 * шапка, тело и итог. Поправить «чуть крупнее» значило найти все три места, а
 * забытое четвёртое (у колонки свой `text-[11px]`) перебивало остальные.
 *
 * <p>Два набора, а не шкала: подробный вид плотный — тринадцать колонок должны
 * влезть по ширине; компактный — из пяти, и там текст крупнее, ради чего режим и
 * включают. Промежуточные значения не нужны, а шкала заставляла бы держать в
 * голове, какое из них где стоит.
 *
 * <p>Высоты строк заданы числом: ячейка шапки рисуется с `py-0`, и высоту, кроме
 * самой строки, задавать нечему. Высота тела — через отступы (`cellPaddingY`),
 * потому что она должна расти вместе с текстом, а не обрезать его.
 */
export interface TableMetrics {
  /** Текст подписи колонки */
  headerText: string
  /** Текст подписи группы в двухуровневой шапке */
  groupText: string
  /** Текст ячейки тела */
  cellText: string
  /** Текст подписи «Итого» в левой ячейке подвала */
  footerLabelText: string
  /** Высота строки шапки в пикселях */
  headerRowHeight: number
  /** Вертикальные отступы ячейки тела: от них зависит высота строки */
  cellPaddingY: string
  /** Вертикальные отступы ячейки подвала */
  footerPaddingY: string
  /** Текст «Показано 1–90 из 120» и листалки под таблицей */
  pagerText: string
}

/** Плотный набор: подробный вид с тринадцатью колонками. */
const NORMAL: TableMetrics = {
  headerText: 'text-[9px]',
  groupText: 'text-[9px]',
  cellText: 'text-[11px]',
  footerLabelText: 'text-[11px]',
  headerRowHeight: 24,
  cellPaddingY: 'py-1',
  footerPaddingY: 'py-1',
  pagerText: 'text-[11.5px]',
}

/** Крупный набор: компактный вид, где колонок пять и места хватает. */
const LARGE: TableMetrics = {
  headerText: 'text-[10.5px]',
  groupText: 'text-[10.5px]',
  cellText: 'text-[12.5px]',
  footerLabelText: 'text-[12px]',
  headerRowHeight: 28,
  cellPaddingY: 'py-1.5',
  footerPaddingY: 'py-1.5',
  pagerText: 'text-[12.5px]',
}

/** Наборы по имени: имя же передаёт вызывающий в `textSize`. */
export const TABLE_METRICS: Record<'normal' | 'large', TableMetrics> = {
  normal: NORMAL,
  large: LARGE,
}
