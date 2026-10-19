package ru.analizer.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Магазина нет или он принадлежит другому пользователю.
 *
 * <p>Отдельный тип нужен, чтобы фронтенд показал «магазин не найден», а не
 * «что-то сломалось». Формат ответа тот же — ошибка, — но с понятным кодом.
 *
 * <p>404, а не 409: магазин приходит в пути запроса, и если его нет, адрес верен,
 * а сам ресурс отсутствует. Состояния «адрес верен, но подключите маркетплейс»
 * больше не бывает — подключение создаёт магазин, и работать можно только с
 * уже существующим.
 *
 * <p>Ответ на «магазина нет» и на «магазин чужой» намеренно одинаковый: различая
 * их, мы сообщали бы злоумышленнику, какие магазины существуют.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AccountNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccountNotFoundException(String accountId) {
        super("Магазин " + accountId + " не найден");
    }
}