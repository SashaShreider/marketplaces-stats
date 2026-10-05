package ru.analizer.marketplace;

/**
 * Реквизиты доступа к API маркетплейса.
 *
 * <p>Передаются в вызов явно, а не хранятся в бине адаптера. Раньше ключи были
 * зашиты в заголовки {@code RestClient} один раз на всё приложение, и этого хватало,
 * пока аккаунт был единственным. Теперь у каждого пользователя свои ключи, поэтому
 * общий бин не может их содержать.
 *
 * <p>Второй вариант — хранить в потокобезопасном поле — не подошёл: фоновые импорты
 * идут в отдельных потоках, и значение там просто потерялось бы.
 *
 * @param apiKey секретный ключ; не попадает ни в один ответ API и ни в логи
 */
public record MarketplaceCredentials(String clientId, String apiKey) {

    public MarketplaceCredentials {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId обязателен");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey обязателен");
        }
    }
}