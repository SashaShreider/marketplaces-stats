/**
 * Примитивы, из которых собраны страницы.
 *
 * <p>Содержимое намеренно тонкое: оформление, а не логика и не предметная область.
 * Всё, что знает про финансы, живёт в `components/finance`, про состояние загрузки —
 * в `components/sync`. Импорт отсюда осознанно идёт через `@/`, чтобы перенос файлов
 * внутри папки не ломал чужой код.
 */

export { AnimatedNumber, useCountUp } from '@/components/ui/AnimatedNumber'
export { MarketplaceLogo } from '@/components/ui/MarketplaceLogo'
export { PageHeader } from '@/components/ui/PageHeader'