import type { AccountInfo } from '@/types'

/**
 * Как показать магазин в интерфейсе.
 *
 * <p>Название от маркетплейса у двух магазинов одной платформы может совпасть — у двух
 * кабинетов одного продавца это обычное дело. Поэтому в селекторе рядом с названием
 * показывается clientId: его продавец узнаёт в своём кабинете, а название может быть
 * общим.
 *
 * <p>Заголовок и списки берут название из `shopName`, а не из названия платформы: «OZON»
 * отвечало бы на вопрос «какая площадка», а не «чей это магазин».
 */
export function shopLabel(account: Pick<AccountInfo, 'shopName' | 'marketplaceName'>): string {
  return account.shopName?.trim() || account.marketplaceName
}

/** Идентификатор для интерфейса: название и clientId, чтобы совпадения не путались. */
export function shopSubLabel(account: Pick<AccountInfo, 'clientId'>): string {
  return account.clientId
}

/**
 * Сравнение магазинов для React-ключа и для поиска выбранного.
 *
 * <p>По коду платформы сравнивать нельзя: у одного продавца их несколько, и выбор
 * оказался бы неоднозначным, а молча показывался бы не тот магазин.
 */
export function sameAccount(a: AccountInfo | null, b: AccountInfo | null): boolean {
  return a !== null && b !== null && a.id === b.id
}