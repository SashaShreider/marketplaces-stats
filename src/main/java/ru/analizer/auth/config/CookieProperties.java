package ru.analizer.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Как выставляются куки сессии и CSRF.
 *
 * <p>По умолчанию {@code SameSite=Lax} и {@code Secure} выключены. Так приложение
 * работает на {@code http://localhost:8080} без дополнительных настроек.
 *
 * <p>Для публикации по HTTPS значения нужно поменять, и вот почему. По умолчанию
 * Spring считает, что {@code Secure} не нужен: безопасная кука просто не поедет
 * в браузере, который не доверяет сертификату. Не выданная кука сессии означает 401
 * на каждом запросе — при этом сервер отвечает 200 на запрос за самой кукой, и
 * диагностика уводит в сторону CSRF. Лучше сказать об этом прямо.
 *
 * @param sameSite политика {@code SameSite} для обеих кук
 * @param secure   добавлять ли флаг {@code Secure} (только для HTTPS)
 */
@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(String sameSite, boolean secure) {

    public CookieProperties {
        sameSite = sameSite == null || sameSite.isBlank() ? "Lax" : sameSite.trim();
    }

    /** Нормализованное значение для {@code ResponseCookie}: {@code Lax} → {@code LAX}. */
    public String sameSiteAttribute() {
        return switch (sameSite.toUpperCase()) {
            case "NONE" -> "None";
            case "STRICT" -> "Strict";
            default -> "Lax";
        };
    }
}