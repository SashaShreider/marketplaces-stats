package ru.analizer.auth.domain;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.auth.domain.AppUser;

/**
 * Источник пользователей для Spring Security.
 *
 * <p>Нужен отдельным сервисом, потому что пользователи создаются в рантайме через
 * регистрацию, а {@code InMemoryUserDetailsManager} берёт список из конфигурации.
 *
 * <p>Роль одна — заведённый пользователь. Блокировки в MVP нет, и оставлять рычаг,
 * которым никто не пользуется, незачем.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AuthService authService;

    public AppUserDetailsService(AuthService authService) {
        this.authService = authService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) {
        AppUser user = authService.requireByLogin(login);
        return User.builder()
                .username(user.getLogin())
                .password(user.getPasswordHash())
                .roles("USER")
                .build();
    }
}