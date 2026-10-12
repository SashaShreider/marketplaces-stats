/**
 * Типы из другого проекта — те самые, на которых написаны
 * `ExternalFinanceTable` и `ExternalProductsView`.
 *
 * <p>Скопированы как есть из `D:\Users\sasha\Desktop\components\types`. В нашем
 * проекте таких типов нет: дневные данные живут в `DayFinance`, товарные — в
 * `ProductRow`, и называются по-другому. Держать копию нужно только пока
 *components сверяются с рабочими: если вид устраивает, типы заменяются на
 * собственные, а файлы удаляются вместе с папкой `preview`.
 */

/** Данные одного дня в том виде, в каком их ждёт внешний компонент таблицы. */
export interface DayFinancialData {
  /** ISO-дата, по ней сортируется и ищется */
  date: string;
  /** Короткая дата для показа, например «7 сент.» */
  displayDate: string;
  /** Полная дата для показа и поиска, например «7 сентября 2026» */
  fullDateStr: string;

  sales: number;
  returns: number;
  partnerPrograms: number;
  income: number;

  commission: number;
  logistics: number;
  otherExpenses: number;
  totalExpenses: number;

  profit: number;
  /** Рентабельность в процентах */
  marginPercent: number;
}

export type MarketplaceId = 'ozon' | 'wb';

/** Товар в том виде, в каком его ждёт внешний компонент списка. */
export interface ProductItem {
  id: string;
  title: string;
  sku: string;
  category: string;
  price: number;
  ordersCount: number;
  revenue: number;
  stock: number;
  rating: number;
  marketplace: MarketplaceId;
}