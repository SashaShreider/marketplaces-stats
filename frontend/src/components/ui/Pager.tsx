import { ChevronLeft, ChevronRight } from 'lucide-react'

/**
 * Пагинация таблицы: номер страницы и стрелки.
 *
 * <p>Одна на обе таблицы — дневную и по товарам. Раздельные копии разъезжались:
 * в одной кнопки отключались по одному счёту страниц, в другой — по другому, и
 * «дальше» на последней странице нажималось в одной таблице, но не в другой.
 */
export default function Pager({
  page,
  pages,
  onPageChange,
}: {
  /** Номер текущей страницы, с нуля. */
  page: number
  /** Сколько страниц всего. */
  pages: number
  onPageChange: (page: number) => void
}) {
  const total = Math.max(1, pages)
  return (
    <div className="flex items-center gap-3">
      <span className="text-[12px] font-semibold text-slate-500">
        Страница <span className="tnum font-extrabold text-slate-800">{page + 1}</span> из{' '}
        <span className="tnum font-extrabold text-slate-800">{total}</span>
      </span>
      <div className="flex gap-1">
        <button
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
          title="Предыдущая страница"
          className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-all hover:border-brand-300 hover:text-brand-600 disabled:cursor-not-allowed disabled:opacity-35"
        >
          <ChevronLeft size={15} />
        </button>
        <button
          disabled={page >= total - 1}
          onClick={() => onPageChange(page + 1)}
          title="Следующая страница"
          className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-all hover:border-brand-300 hover:text-brand-600 disabled:cursor-not-allowed disabled:opacity-35"
        >
          <ChevronRight size={15} />
        </button>
      </div>
    </div>
  )
}
