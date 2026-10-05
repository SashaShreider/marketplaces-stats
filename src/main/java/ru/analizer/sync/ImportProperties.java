package ru.analizer.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры синхронизации.
 *
 * @param maxPageRetries  сколько раз перезапросить страницу с начала, если OZON отверг
 *                        {@code last_id}: курсор живёт 15 минут и может «протухнуть»
 *                        прямо во время обхода дня
 * @param maturityDays    окно зрелости в днях. День считается окончательным, только если
 *                        он старше этого окна: начисления за свежие дни ещё приходят.
 *                        Значение уточняется по накопленной статистике изменений
 * @param backgroundTimeoutSeconds сколько ждать фоновой задачу при старте приложения,
 *                        прежде чем признать её прерванной
 */
@ConfigurationProperties(prefix = "sync")
public record ImportProperties(
        int maxPageRetries,
        int maturityDays,
        int backgroundTimeoutSeconds
) {
    public ImportProperties {
        if (maturityDays <= 0) {
            maturityDays = 3;
        }
        if (backgroundTimeoutSeconds <= 0) {
            backgroundTimeoutSeconds = 30;
        }
    }
}