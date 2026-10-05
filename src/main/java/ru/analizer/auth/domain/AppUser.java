package ru.analizer.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Пользователь приложения.
 *
 * <p>Владеет аккаунтами продавцов, а через них — всеми начислениями, каталогом
 * и прогоном импортов. Без этого пользователи видели бы чужие данные.
 *
 * <p>Пароль хранится только в виде BCrypt-хэша. Восстановления пароля пока нет:
 * адреса почты нет, подтверждения нет.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login", nullable = false, unique = true, length = 64)
    private String login;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String login, String passwordHash, String displayName) {
        this.login = login;
        this.passwordHash = passwordHash;
        this.displayName = displayName == null || displayName.isBlank() ? login : displayName;
    }

    public Long getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}