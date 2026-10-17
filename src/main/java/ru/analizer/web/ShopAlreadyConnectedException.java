package ru.analizer.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Этот кабинет маркетплейса уже подключён к аккаунту.
 *
 * <p>Повторное подключение того же магазина запрещено уникальным ограничением в базе:
 * два аккаунта с одинаковыми реквизитами означали бы, что один кабинет импортируется
 * дважды и пополам попадает в два отчёта.
 *
 * <p>409, а не 400: запрос верен, но состояние не позволяет его выполнить. Смена ключей
 * уже подключённого магазина — другое действие, и оно доступно всегда.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ShopAlreadyConnectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ShopAlreadyConnectedException(String marketplaceCode, String clientId) {
        super("Магазин с Client-Id " + clientId + " на " + marketplaceCode + " уже подключён. "
                + "Чтобы сменить ключи, откройте его в списке магазинов");
    }
}