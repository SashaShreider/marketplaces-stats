import type { DayFinance, ProductRow } from '@/types'
import { fmtDateLong, fmtDayShort } from '@/utils/format'
import type { DayFinancialData, MarketplaceId, ProductItem } from './types'

/**
 * Перевод наших данных в форму, которую ждут компоненты из другого проекта.
 *
 * <p>Главное расхождение — знак расходов. В нашем проекте `commission`,
 * `logistics` и `other` приходят отрицательными: так их удобнее складывать в
 * отчёте, где расходы вычитаются из дохода. Во внешнем проекте расходы
 * положительные, о чём говорит само поле `totalExpenses`. Поэтому здесь
 * берётся модуль — иначе «Итого» сошлось бы неправильно и вид был бы
 * нечестным.
 */

/** Наш день → день внешнего компонента. */
export function toExternalDay(d: DayFinance): DayFinancialData {
  return {
    date: d.date,
    displayDate: fmtDayShort(d.date),
    fullDateStr: fmtDateLong(d.date),
    sales: d.sales,
    returns: d.returns,
    partnerPrograms: d.partners,
    income: d.income,
    commission: Math.abs(d.commission),
    logistics: Math.abs(d.logistics),
    otherExpenses: Math.abs(d.other),
    totalExpenses: d.expenses,
    profit: d.profit,
    marginPercent: d.income > 0 ? Math.round((d.profit / d.income) * 100) : 0,
  }
}

/**
 * Наш товар → товар внешнего компонента.
 *
 * <p>Часть полей в нашем API просто нет: цена, остаток на складе, рейтинг и
 * категория. Заполняются правдоподобными значениями — только чтобы оценить вид.
 * На странице предпросмотра это помечено явно, иначе правдоподобные числа можно
 * принять за настоящие.
 */
export function toExternalProduct(r: ProductRow, marketplace: MarketplaceId): ProductItem {
  // Остаток и цена считаются от идентификатора товара: значение постоянное между
  // перезагрузками, и это важно — иначе строки «прыгали» бы при каждом рендере.
  const seed = (r.sku % 97) + 8

  return {
    id: String(r.sku),
    title: r.name,
    sku: r.offerId ?? String(r.sku),
    category: r.typeId ? `Категория ${r.typeId}` : 'Без категории',
    price: 180 + (seed % 40) * 25,
    ordersCount: r.soldQuantity ?? r.quantity ?? 0,
    revenue: r.income,
    stock: (seed * 7) % 160,
    rating: 4 + (seed % 10) / 10,
    marketplace,
  }
}