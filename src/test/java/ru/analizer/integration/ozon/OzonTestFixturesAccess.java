package ru.analizer.integration.ozon;

import ru.analizer.integration.model.AccrualDto;

import java.io.IOException;
import java.util.List;

/**
 * Доступ к разбору фикстур из тестов аналитики.
 *
 * <p>Разбор единственный и общий: и тесты транспорта, и тесты финансовой модели
 * должны видеть ровно те же данные, что и боевое приложение.
 */
public final class OzonTestFixturesAccess {

    private OzonTestFixturesAccess() {
    }

    public static List<AccrualDto> parse(String fixture) {
        try {
            return OzonTestFixtures.load(fixture);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать фикстуру " + fixture, e);
        }
    }
}
