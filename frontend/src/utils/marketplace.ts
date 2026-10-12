import type { MarketplaceInfo } from '@/types'

/**
 * Название магазина для интерфейса.
 *
 * <p>У маркетплейса два разных имени, и путать их легко: {@code name} — это название
 * площадки из справочника («OZON»), а {@code accountName} — конкретный подключённый
 * магазин («OZON 1154»). В заголовке «Финансовые результаты магазина …» подразумевается
 * второе: «OZON» отвечало бы на вопрос «какая площадка», а не «чей это магазин».
 *
 * <p>Фолбэк на {@code name} нужен для неподключённого магазина, у которого
 * {@code accountName} пуст: пустое место в заголовке читалось бы как ошибка.
 */
export function shopName(mp: Pick<MarketplaceInfo, 'name' | 'accountName'>): string {
  return mp.accountName?.trim() || mp.name
}
