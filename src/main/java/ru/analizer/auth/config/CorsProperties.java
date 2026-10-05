package ru.analizer.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Настройки доступа из браузера.
 *
 * <p><b>По умолчанию список пуст, и это не ошибка настройки, а решение.</b> Приложение
 * работает только со своего источника: фронтенд отдаётся с того же хоста, что и API.
 * Тогда CORS не нужен вовсе, а сессионная кука не покидает свой домен.
 *
 * <p>Список наполняется только когда фронтенд действительно живёт отдельно — например,
 * `vite` на `http://localhost:5173` во время разработки. Без этого параметра запрос с
 * другого источника не дойдёт: браузер заблокирует ответ, и ошибка будет выглядеть
 * как «сервер молчит».
 *
 * @param allowedOrigins источники, которым разрешены запросы с куками. Пусто — только
 *                      свой источник. {@code "*"} использовать нельзя: браузер не
 *                      примет его вместе с кукой, а выглядеть будет как разрешённое
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    /** Разрешено ли что-то сверх своего источника. */
    public boolean hasAllowedOrigins() {
        return !allowedOrigins.isEmpty();
    }
}