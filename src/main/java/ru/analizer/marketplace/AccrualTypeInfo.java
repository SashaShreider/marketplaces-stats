package ru.analizer.marketplace;

/**
 * Элемент справочника типов начислений в маркетплейс-независимом виде.
 *
 * <p>Контракт адаптера не должен отдавать DTO конкретного маркетплейса: иначе аналитика
 * и синхронизация зависели бы от OZON, а добавление Wildberries потребовало бы их правок.
 */
public record AccrualTypeInfo(Integer externalId, String name, String description) {
}
