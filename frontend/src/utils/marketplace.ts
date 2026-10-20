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