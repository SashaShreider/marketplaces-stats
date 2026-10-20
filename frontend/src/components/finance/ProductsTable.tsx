import { useState } from 'react'
import { ChevronLeft, ChevronRight, ExternalLink, Package } from 'lucide-react'
import type { ProductRow, SelectionTotals } from '@/types'
import { cn } from '@/utils/cn'
import { fmtMoney, fmtNum, revenue } from '@/utils/format'

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

/** Порядковый номер строки на текущей странице: нужен, чтобы о ссылку на позицию можно было сказать вслух. */
function Row({ r, n }: { r: ProductRow; n: number }) {
  const sold = r.soldQuantity
  const returned = r.returnedQuantity
  const authors = r.authors.map((a) => a.raw)
  const gross = revenue(r.income, r.expenses)
  // Минус у расходов ставим сами, а не берём из fmtMoney. Проверять надо
  // округлённое значение: у копеечных остатков вроде −0.4 сумма округляется
  // в ноль, и проверка на исходное число печатала «−0 ₽» — расход, которого нет,
  // выглядел как вычет.
  const expenses = Math.abs(r.expenses ?? 0)
  const hasExpenses = Math.round(expenses) !== 0
  return (
    <tr className="group transition-colors hover:bg-brand-50/50">
      <td className="tnum w-10 border-b border-slate-100 px-2 py-3 text-right align-top text-[11.5px] font-semibold text-slate-300 group-hover:border-brand-100 group-hover:text-slate-400">
        {n}
      </td>
      <td className="border-b border-slate-100 px-4 py-3 group-hover:border-brand-100">
        <div className="flex items-center gap-3">
          <Thumb src={r.primaryImage} name={r.name} />
          <div className="min-w-0 max-w-[420px]">
            <div className="flex items-start gap-2">
              <div className="line-clamp-2 text-[13px] font-bold leading-snug text-slate-800" title={r.name}>
                {r.name}
              </div>
              {/* Архивный товар выглядит как обычный, пока не сказать иначе: у него
                  настоящие продажи и расходы, и без отметки продавец искал бы его
                  в кабинете, где его уже нет. */}
              {r.archived && (
                <span
                  title="Товар снят с продажи и убран в архив OZON — в кабинете его уже нет"
                  className="mt-px shrink-0 rounded-md bg-slate-100 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-slate-500 ring-1 ring-slate-200"
                >
                  Архив
                </span>
              )}
              {/* Ссылка на карточку в OZON. Отдельная кнопка, а не ссылка на
                  названии: в таблице названий много, и кликать по тексту опасно —
                  промахнувшись, человек уходил бы из отчёта, ничего не выбрав. */}
              <a
                href={`https://www.ozon.ru/product/${r.sku}`}
                target="_blank"
                rel="noopener noreferrer"
                title="Открыть карточку в OZON"
                className="mt-px flex h-5 w-5 shrink-0 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
              >
                <ExternalLink size={12} />
              </a>
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
        {/** 
        {r.offerId && <div className="text-[11px] font-medium text-slate-400">{r.offerId}</div>}
        */}
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
<td className="tnum whitespace-nowrap border-b border-slate-100 px-4 py-3 text-right text-[13px] font-semibold text-slate-700 group-hover:border-brand-100">
          {hasExpenses ? `−${fmtMoney(expenses)}` : <span className="text-slate-300">0 ₽</span>}
        </td>
      <td
        className={cn(
          'tnum whitespace-nowrap border-b border-slate-100 px-4 py-3 text-right text-[13px] font-extrabold group-hover:border-brand-100',
          gross >= 0 ? 'text-slate-900' : 'text-rose-600',
        )}
      >
        {fmtMoney(gross)}
      </td>
    </tr>
  )
}

const HEAD = [
  ['№', 'text-right'],
  ['Товар', 'text-left'],
  ['SKU / артикул', 'text-left'],
  ['Продано', 'text-right'],
  ['Доход', 'text-right'],
  ['Расходы', 'text-right'],
  ['Выручка', 'text-right'],
] as const

export default function ProductsTable({
  rows,
  selectionTotals,
  page,
  size,
  totalRows,
  totalPages,
  filtered,
  onPageChange,
}: {
  rows: ProductRow[]
  /** Итог по всему отбору: приходит с сервера, а не считается по rows */
  selectionTotals: SelectionTotals
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

  // Подвал с кнопками страниц нужен, только когда есть куда листать. Когда всё
  // поместилось, он показывал бы «Страница 1 из 1» и две нерабочие кнопки —
  // шум вместо счётчика строк, который в этом случае единственное, что полезно.
  const showPager = totalRows > size

  // Итог приходит с сервера: бэкенд считает его по всем товарам отбора, а строк
  // отдаёт только текущую страницу. Складывать показанное — значит показать
  // товары одной страницы и подписать это «Итого».
  const gross = revenue(selectionTotals.income, selectionTotals.expenses)
  // Ноль расходов показываем нулём, а не «−0 ₽»: иначе итог выглядел бы
  // вычетом, которого не было.
  const hasExpenses = Math.round(selectionTotals.expenses) !== 0

  return (
    <div className="mt-4 overflow-hidden rounded-xl border border-slate-200/80">
      <div className="scroll-slim max-h-[620px] overflow-auto">
        <table className="w-full min-w-[900px] border-separate border-spacing-0">
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
            {rows.map((r, i) => (
              <Row key={r.sku} r={r} n={firstShown + i} />
            ))}
            {rows.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-14 text-center text-[13px] font-medium text-slate-400">
                  {filtered ? 'По вашему запросу ничего не найдено' : 'Товаров нет'}
                </td>
              </tr>
            )}
          </tbody>
          {rows.length > 0 && (
            <tfoot className="sticky bottom-0 z-10 bg-slate-50/95 backdrop-blur">
              <tr className="shadow-[0_-1px_0_0_rgb(226_232_240)]">
                <td
                  colSpan={3}
                  className="border-t border-slate-200 px-4 py-2.5 text-left text-[11.5px] font-extrabold uppercase tracking-[0.06em] text-slate-500"
                >
                  Итого
                </td>
                <td className="tnum border-t border-slate-200 px-4 py-2.5 text-right text-[13px] font-extrabold text-slate-800">
                  {fmtNum(selectionTotals.soldQuantity)}
                </td>
                <td className="tnum border-t border-slate-200 px-4 py-2.5 text-right text-[13px] font-extrabold text-slate-900">
                  {fmtMoney(selectionTotals.income)}
                </td>
<td className="tnum border-t border-slate-200 px-4 py-2.5 text-right text-[13px] font-extrabold text-slate-700">
                      {hasExpenses ? `−${fmtMoney(selectionTotals.expenses)}` : '0 ₽'}
                    </td>
                <td
                  className={cn(
                    'tnum border-t border-slate-200 px-4 py-2.5 text-right text-[13px] font-extrabold',
                    gross >= 0 ? 'text-slate-900' : 'text-rose-600',
                  )}
                >
                  {fmtMoney(gross)}
                </td>
              </tr>
            </tfoot>
          )}
        </table>
      </div>

      {(showPager || rows.length === 0) && (
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
          {showPager && (
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
          )}
        </div>
      )}
    </div>
  )
}
