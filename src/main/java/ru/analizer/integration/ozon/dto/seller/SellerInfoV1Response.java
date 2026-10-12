package ru.analizer.integration.ozon.dto.seller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ответ {@code POST /v1/seller/info}.
 *
 * <p>Метод возвращает много — реквизиты компании, подписку, рейтинги по каждой метрике,
 * — но нужна отсюда одна строка: как магазин называет сам себя. Остальное не
 * разбирается намеренно. Рейтинги в ответе меняются каждый день и не имеют отношения
 * к тому, как аккаунт называется; сохранять их значило бы держать в базе данные,
 * которые никто не читает и которые устаревают быстрее, чем обновляются.
 *
 * <p>Пустые и отсутствующие поля — норма, а не ошибка: ответ у продавца-ИП может прийти
 * без {@code company}, и это не повод падать при подключении.
 *
 * @param company сведения о компании; может отсутствовать
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SellerInfoV1Response(
        @JsonProperty("company") Company company
) {

    /**
     * Название магазина так, как оно указано у продавца.
     *
     * @param company сведения о компании продавца
     * @param name    название компании; может отсутствовать или оказаться пустым
     * @param inn     ИНН; тот же случай — у продавца может не быть компании
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Company(
            @JsonProperty("name") String name,
            @JsonProperty("legal_name") String legalName,
            @JsonProperty("inn") String inn
    ) {
    }

    /**
     * Название магазина или {@code null}, если взять нечего.
     *
     * <p>Отдельный метод вместо {@code company().name()}: отсутствующий блок
     * {@code company} — это {@code null}, а не пустой объект, и без проверки на обоих
     * уровнях здесь был бы {@link NullPointerException} ровно в момент подключения.
     */
    public String shopNameOrNull() {
        if (company == null) {
            return null;
        }
        String value = company.name();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
