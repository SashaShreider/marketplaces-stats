package ru.analizer.account.domain;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Логин пользователя текущего запроса.
 *
 * <p>Отдельный класс, а не чтение {@code SecurityContextHolder} по месту: иначе при
 * добавлении второго маркетплейса правило «работаем только с данными текущего
 * пользователя» пришлось бы вспоминать каждый раз заново. Здесь оно записано один раз.
 *
 * <p><b>Не работает в фоновых потоках.</b> {@code SecurityContextHolder} держит
 * значение в {@code ThreadLocal} и в задаче пула потоков будет пустым. Фоновые
 * импорты поэтому передают {@code accountId} параметром и не полагаются на этот класс.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** @throws org.springframework.security.access.AccessDeniedException если нет сессии */
    public static String login() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Нет аутентифицированного пользователя");
        }
        return authentication.getName();
    }

    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}