package ru.analizer.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import ru.analizer.auth.domain.AppUser;

/**
 * Подключённый аккаунт продавца.
 *
 * <p>Принадлежит пользователю: через эту связь изолируются начисления, каталог
 * и прогоны импортов. Один магазин на маркетплейс у пользователя — ограничение
 * в базе, а не в коде.
 *
 * <p>API-ключ хранится в базе, чтобы пользователь мог подключить маркетплейс
 * из интерфейса, и потому приложение разворачивается на любом числе продавцов.
 *
 * <p>TODO(#encrypt-credentials): ключ хранится в открытом виде. Утечка дампа базы
 * даёт доступ ко всем магазинам. Планируется AES-GCM и мастер-ключ в
 * APP_ENCRYPTION_KEY; см. V9__users_and_credentials.sql.
 */
@Entity
@Table(name = "seller_account")
public class SellerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marketplace_id", nullable = false)
    private Marketplace marketplace;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    /**
     * Секретный ключ маркетплейса.
     *
     * <p>Не возвращается ни в одном ответе API и не пишется в логи.
     */
    @Column(name = "api_key", length = 255)
    private String apiKey;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected SellerAccount() {
    }

    public SellerAccount(AppUser user, Marketplace marketplace, String name,
                         String clientId, String apiKey) {
        this.user = user;
        this.marketplace = marketplace;
        this.name = name;
        this.clientId = clientId;
        this.apiKey = apiKey;
    }

    /** Замена реквизитов: ключи у маркетплейсов истекают, менять их приходится. */
    public void updateCredentials(String clientId, String apiKey) {
        this.clientId = clientId;
        this.apiKey = apiKey;
    }

    public boolean hasCredentials() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Реквизиты для вызова маркетплейса.
     *
     * <p>Собираются здесь, а не в сервисах импорта, потому что ключи лежат в одной
     * сущности и разбирать их в шести местах — значит забыть в одном.
     *
     * @throws IllegalStateException ключ не задан: импорт не должен падать внутри
     *                               адаптера, где причина неочевидна
     */
    public ru.analizer.account.domain.MarketplaceCredentials credentials() {
        if (!hasCredentials()) {
            throw new IllegalStateException(
                    "У аккаунта " + id + " не задан api_key: сначала подключите маркетплейс");
        }
        return new ru.analizer.account.domain.MarketplaceCredentials(clientId, apiKey);
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Marketplace getMarketplace() {
        return marketplace;
    }

    public String getName() {
        return name;
    }

    public String getClientId() {
        return clientId;
    }

    public String getApiKey() {
        return apiKey;
    }
}
