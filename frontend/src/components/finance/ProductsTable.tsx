import { useState } from 'react'
import { ChevronLeft, ChevronRight, Package } from 'lucide-react'
import type { ProductRow } from '@/types'
import { cn } from '@/utils/cn'
import { fmtMoney, fmtNum } from '@/utils/format'

/**
 * Таблица товаров: строки, прокрутка и пагинация.
 *
 * <p>Вынесена из страницы отдельным компонентом не из-за размера, а из-за
 * устройства страницы: у неё есть работа с данными — запросы, фильтры, отмена
 * устаревших ответов — и смешивать её с вёрсткой таблицы означало держать в
 * одном файле и то, и то. Здесь осталось только отображение.
 */

function Thumb({ src, name }: { src: string | null; name: string }) {
  const [failed, setFailed] = useState(false)
  if (!src || failed) {
    return (
      <span className="flex h-12 w-9 shrink-0 items-center justify-center rounded-md bg-slate-100 text-slate-300">
        <Package size={16} />
      </span>
    )
  }
  return (
    <img
      src={src}
      alt={name}
      loading="lazy"
      onError={() => setFailed(true)}
      className="h-12 w-9 rounded-md bg-slate-100 object-cover ring-1 ring-slate-200/70"
    />
  )
}

function Row({ r }: { r: ProductRow }) {
  const sold = r.soldQuantity ?? r.quantity ?? 0
  const returned = r.returnedQuantity ?? 0
  const authors = r.authors.map((a) => a.raw)
  return (
    <tr className="group transition-colors hover:bg-brand-50/50">
      <td className="border-b border-slate-100 px-4 py-3 group-hover:border-brand-100">
        <div className="flex items-center gap-3">
          <Thumb src={r.primaryImage} name={r.name} />
          <div className="min-w-0 max-w-[420px]">
            <div className="line-clamp-2 text-[13px] font-bold leading-snug text-slate-800" title={r.name}>
              {r.name}
            </div>
            <div className="mt-0.5 truncate text-[11.5px] font-medium text-slate-400">
              {authors.length > 0 ? authors.join(', ') : 'Автор не указан'}
              {r.isbn && <span> · ISBN {r.isbn}</span>}
            </div>
          </div>
        </div>
      </td>
      <td className="whitespace-nowrap border-b border-slate-100 px-4 py-3 text-left group-hover:border-brand-100">
        <div className="tnum text-[12.5px] font-bold text-slate-700">{r.sku}</div>
        {r.offerId && <div className="text-[11px] font-medium text-slate-400">{r.offerId}</div>}
      </td>
      <td className="whitespace-nowrap border-b border-slate-100 px-4 py-3 text-right group-hover:border-brand-100">
        <span className={cn('tnum text-[13px] font-bold', sold > 0 ? 'text-slate-800' : 'text-slate-300')}>
          {fmtNum(sold)}
        </span>
        {returned > 0 && (
          <span className="tnum ml-1.5 text-[11px] font-semibold text-rose-500">−{fmtNum(returned)}</span>
        )}
      </td>
      <td className="tnum whitespace-nowrap border-b border-slate-100 px-4 py-3 text-right text-[13px] font-extrabold text-slate-900 group-hover:border-brand-100">
        {r.income ? fmtMoney(r.income) : <span className="text-slate-300">0 ₽</span>}
      </td>
      <td className="tnum whitespace-nowrap border-b border-slate-100 px-4 py-3 text-right text-[13px] font-semibold text-rose-500 group-hover:border-brand-100">
        {r.expenses ? `−${fmtMoney(Math.abs(r.expenses))}` : <span className="text-slate-300">0 ₽</span>}
      </td>
    </tr>
  )
}

const HEAD = [
  ['Товар', 'text-left'],
  ['SKU / артикул', 'text-left'],
  ['Продано', 'text-right'],
  ['Доход', 'text-right'],
  ['Расходы', 'text-right'],
] as const

export default function ProductsTable({
  rows,
  page,
  size,
  totalRows,
  totalPages,
  filtered,
  onPageChange,
}: {
  rows: ProductRow[]
  /** Номер страницы с нуля */
  page: number
  size: number
  totalRows: number
  totalPages: number
  /** Применён ли фильтр: от него зависит текст пустой выборки */
  filtered: boolean
  onPageChange: (page: number) => void
}) {
  const firstShown = rows.length ? page * size + 1 : 0
  const lastShown = page * size + rows.length

  return (
    <div className="mt-4 overflow-hidden rounded-xl border border-slate-200/80">
      <div className="scroll-slim max-h-[620px] overflow-auto">
        <table className="w-full min-w-[820px] border-separate border-spacing-0">
          <thead>
            <tr>
              {HEAD.map(([label, align]) => (
                <th
                  key={label}
                  className={cn(
                    'sticky top-0 z-10 whitespace-nowrap border-b border-slate-200 bg-slate-50/95 px-4 py-3 text-[10.5px] font-extrabold uppercase tracking-[0.08em] text-slate-400 backdrop-blur',
                    align,
                  )}
                >
                  {label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <Row key={r.sku} r={r} />
            ))}
            {rows.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-14 text-center text-[13px] font-medium text-slate-400">
                  {filtered ? 'По вашему запросу ничего не найдено' : 'Товаров нет'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between gap-3 border-t border-slate-100 px-4 py-2">
        <span className="text-[12px] font-medium text-slate-400">
          {rows.length > 0 ? (
            <>
              Показано <span className="tnum font-bold text-slate-600">{firstShown}</span>–
              <span className="tnum font-bold text-slate-600">{lastShown}</span> из{' '}
              <span className="tnum font-bold text-slate-600">{fmtNum(totalRows)}</span>
            </>
          ) : (
            <>
              Ничего не найдено из{' '}
              <span className="tnum font-bold text-slate-600">{fmtNum(totalRows)}</span>
            </>
          )}
        </span>
        <div className="flex items-center gap-3">
          <span className="text-[12px] font-semibold text-slate-500">
            Страница <span className="tnum font-extrabold text-slate-800">{page + 1}</span> из{' '}
            <span className="tnum font-extrabold text-slate-800">{Math.max(1, totalPages)}</span>
          </span>
          <div className="flex gap-1">
            <button
              disabled={page === 0}
              onClick={() => onPageChange(page - 1)}
              title="Предыдущая страница"
              className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-all hover:border-brand-300 hover:text-brand-600 disabled:cursor-not-allowed disabled:opacity-35"
            >
              <ChevronLeft size={15} />
            </button>
            <button
              disabled={page >= totalPages - 1}
              onClick={() => onPageChange(page + 1)}
              title="Следующая страница"
              className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-all hover:border-brand-300 hover:text-brand-600 disabled:cursor-not-allowed disabled:opacity-35"
            >
              <ChevronRight size={15} />
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}