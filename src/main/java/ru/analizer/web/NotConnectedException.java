package ru.analizer.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Маркетплейс не подключён или аккаунт принадлежит другому пользователю.
 *
 * <p>Отдельный тип нужен, чтобы фронтенд показал «подключите маркетплейс», а не
 * «что-то сломалось». Формат ответа тот же — ошибка, — но с понятным кодом.
 *
 * <p>Ответ на «аккаунт чужой» и на «аккаунта нет» намеренно одинаковый: различая
 * их, мы сообщали бы злоумышленнику, какие аккаунты существуют.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class NotConnectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotConnectedException(String marketplaceCode) {
        super("Маркетплейс " + marketplaceCode + " не подключён. Задайте реквизиты: "
                + "PUT /api/marketplaces/" + marketplaceCode.toLowerCase(java.util.Locale.ROOT)
                + "/credentials");
    }
}