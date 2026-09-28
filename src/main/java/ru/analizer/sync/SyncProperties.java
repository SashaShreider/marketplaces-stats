package ru.analizer.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры сервиса синхронизации.
 *
 * @param maxPageRetries сколько раз перезапросить страницу с начала, если OZON отверг
 *                       {@code last_id}. Курсор живёт 15 минут, поэтому страница может
 *                       «протухнуть» прямо во время обхода дня.
 */
@ConfigurationProperties(prefix = "sync")
public record SyncProperties(int maxPageRetries) {
}
