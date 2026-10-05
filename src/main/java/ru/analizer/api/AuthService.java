package ru.analizer.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.persistence.entity.AppUser;
import ru.analizer.persistence.repository.AppUserRepository;

/**
 * Регистрация, вход и чтение текущего пользователя.
 *
 * <p>Пользователи хранятся в своей таблице, а Spring Security получает их через
 * {@link AppUserDetailsService}. Отдельная реализация {@code UserDetailsService}
 * вместо {@code InMemoryUserDetailsManager} нужна потому, что пользователи теперь
 * появляются в рантайме, а не задаются конфигурацией.
 *
 * <p>Пароль хэшируется BCrypt и хранится только хэшем. Проверку входа выполняет
 * {@code AuthenticationManager} Spring Security, а не этот класс: одна реализация
 * аутентификации вместо двух, которые со временем разошлись бы (одна начнёт
 * учитывать блокировку, другая нет).
 *
 * <p>Восстановления пароля нет: почты нет. TODO(#password-reset) рядом с добавлением
 * email — без него забытый пароль восстанавливается только правкой базы.
 */
@Service
public class AuthService {

    /** Минимальная длина: короче 8 пароль подбирается без усилий. */
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_LOGIN_LENGTH = 64;

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser register(String login, String password, String displayName) {
        String normalizedLogin = normalizeLogin(login);
        validatePassword(password);

        if (appUserRepository.existsByLogin(normalizedLogin)) {
            throw new LoginTakenException(normalizedLogin);
        }
        try {
            return appUserRepository.save(new AppUser(
                    normalizedLogin, passwordEncoder.encode(password), displayName));
        } catch (DataIntegrityViolationException e) {
            // Два одинаковых запроса регистрации прошли проверку existsByLogin почти
            // одновременно. Отвечаем так же, как и при последовательной регистрации,
            // иначе клиент увидит 500 вместо понятного «логин занят».
            throw new LoginTakenException(normalizedLogin);
        }
    }

    public AppUser requireByLogin(String login) {
        return appUserRepository.findByLogin(login)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
    }

    private static String normalizeLogin(String login) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("Логин обязателен");
        }
        String trimmed = login.trim();
        if (trimmed.length() > MAX_LOGIN_LENGTH) {
            throw new IllegalArgumentException("Логин длиннее " + MAX_LOGIN_LENGTH + " символов");
        }
        if (trimmed.contains(" ")) {
            throw new IllegalArgumentException("Логин не должен содержать пробелов");
        }
        return trimmed;
    }

    private static void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Пароль обязателен");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Пароль короче " + MIN_PASSWORD_LENGTH + " символов");
        }
    }

    /** Логин уже занят. */
    public static class LoginTakenException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public LoginTakenException(String login) {
            super("Логин " + login + " уже занят");
        }
    }
}