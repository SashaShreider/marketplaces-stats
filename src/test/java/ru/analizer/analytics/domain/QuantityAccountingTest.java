package ru.analizer.analytics.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.analizer.integration.model.AccrualDto;
import ru.analizer.integration.ozon.OzonTestFixturesAccess;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Количество проданных и возвращённых единиц.
 *
 * <p>Отдельный класс, а не ещё пара тестов в общем с деньгами: у количества другая
 * природа ошибки. Деньги при неверной классификации строк не меняются — нулевые строки
 * дают ноль, и тождество «доходы − расходы = выплате» остаётся верным. Ломается только
 * количество, и чем тише. Проверять приходится отдельно, иначе правильность денег
 * постоянно маскировала бы неправильность счёта.
 *
 * <p>Данные настоящие, за 2026-04-11: продажа двух единиц одной позиции, удержание без
 * комиссии и возврат. Значения сверены с тождеством по каждой операции.
 */
class QuantityAccountingTest {

    private static final String FIXTURE = "fixtures/accruals-quantity-2026-04-11.json";
    private static final LocalDate DAY = LocalDate.of(2026, 4, 11);

    /** Ручной подсчёт по файлу-фикстуре. */
    private static final String SALES = "1398.10";
    private static final String RETURNS = "-1039.05";
    /** 2051.92 + 13.98 − 674.56 − 10.39. */
    private static final String PARTNER = "1380.95";
    /** −1593.44 + 672.36: у возврата комиссия положительна. */
    private static final String COMMISSION = "-921.08";
    /** −254.40 у продажи и −129.64 у удержания. */
    private static final String LOGISTICS = "-384.04";
    private static final String PAYOUT = "434.88";

    private static final FinancialSummary SUMMARY = FinancialModel.summarize(
            DAY, DAY, products(), fees(), Map.of(DAY, new BigDecimal(PAYOUT)));

    @Test
    @DisplayName("Проданы две единицы одной позиции: quantity больше единицы бывает")
    void quantityGreaterThanOneIsCounted() {
        assertThat(SUMMARY.soldQuantity()).isEqualTo(2);
        // При двух единицах sale_price приходит за позицию, а не за штуку: делить его
        // на количество здесь нельзя, иначе выручка уменьшилась бы вдвое.
        assertThat(SUMMARY.sales()).isEqualByComparingTo(SALES);
    }

    @Test
    @DisplayName("Возврат считается отдельно и не уменьшает число проданных")
    void returnsAreCountedSeparately() {
        assertThat(SUMMARY.returnedQuantity())
                .as("quantity у возврата положителен: уменьшение несут деньги, не количество")
                .isEqualTo(1);
        assertThat(SUMMARY.soldQuantity())
                .as("возврат не должен попадать в проданные")
                .isEqualTo(2);
        assertThat(SUMMARY.returns()).isEqualByComparingTo(RETURNS);
    }

    @Test
    @DisplayName("Удержание без комиссии не считается продажей")
    void rowsWithoutCommissionAreNotSales() {
        // Строка с quantity = 1 и без sale_price — это штраф за доставку. На настоящих
        // данных апреля и сентября 2026 таких строк 139 при 555 настоящих продажах:
        // включение завысило бы количество на двадцать процентов, и заметить это можно
        // было бы, лишь сверив число с кабинетом руками.
        assertThat(SUMMARY.soldQuantity() + SUMMARY.returnedQuantity())
                .as("всего единиц три: две проданы, одна возвращена, третья строка не продажа")
                .isEqualTo(3);
        assertThat(SUMMARY.logistics())
                .as("логистика удержания остаётся расходом")
                .isEqualByComparingTo(LOGISTICS);
    }

    @Test
    @DisplayName("Количество не меняет деньги: тождество сходится")
    void quantityDoesNotDisturbMoney() {
        assertThat(SUMMARY.partnerProgramme()).isEqualByComparingTo(PARTNER);
        assertThat(SUMMARY.commission()).isEqualByComparingTo(COMMISSION);
        assertThat(SUMMARY.income()).isEqualByComparingTo("1740.00");
        assertThat(SUMMARY.expenses()).isEqualByComparingTo("1305.12");
        assertThat(SUMMARY.reconciles())
                .as("доходы − расходы должны равняться выплате")
                .isTrue();
    }

    @Test
    @DisplayName("Чистое количество считается, но не подменяет собой показатели")
    void netQuantityIsAvailableButSeparate() {
        assertThat(SUMMARY.netQuantity()).isEqualTo(1);
        assertThat(SUMMARY.soldQuantity())
                .as("проданные не уменьшаются возвратами: показатели стоят рядом")
                .isEqualTo(2);
        assertThat(SUMMARY.returnedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Сложение дней даёт сумму количеств")
    void quantityAddsUpAcrossDays() {
        FinancialSummary combined = SUMMARY.plus(SUMMARY);

        assertThat(combined.soldQuantity()).isEqualTo(4);
        assertThat(combined.returnedQuantity()).isEqualTo(2);
    }

    private static List<ProductFact> products() {
        List<ProductFact> result = new ArrayList<>();
        for (AccrualDto accrual : OzonTestFixturesAccess.parse(FIXTURE)) {
            if (accrual.posting() == null) {
                continue;
            }
            for (AccrualDto.Product product : accrual.posting().products()) {
                AccrualDto.Commission c = product.commission();
                result.add(new ProductFact(
                        DAY, accrual.unitNumber(), accrual.externalId(), product.sku(),
                        product.quantity(),
                        // null здесь — осмысленное значение, а не заглушка: так выглядит
                        // строка, у которой OZON не прислала commission вовсе.
                        c == null ? null : c.salePrice(),
                        c == null ? null : c.saleCommission(),
                        c == null ? null : c.bonus(),
                        c == null ? null : c.coinvestment()));
            }
        }
        return result;
    }

    private static List<FeeFact> fees() {
        List<FeeFact> result = new ArrayList<>();
        for (AccrualDto accrual : OzonTestFixturesAccess.parse(FIXTURE)) {
            if (accrual.posting() == null) {
                continue;
            }
            for (AccrualDto.Product product : accrual.posting().products()) {
                for (AccrualDto.FeeDetail service : product.deliveryServices()) {
                    result.add(new FeeFact(DAY, accrual.unitNumber(), accrual.externalId(),
                            product.sku(), service.typeId(), service.amount(),
                            FeeFact.FeeKind.DELIVERY));
                }
            }
        }
        return result;
    }
}
