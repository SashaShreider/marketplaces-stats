package ru.analizer.analytics.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.analizer.integration.ozon.OzonTestFixturesAccess;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Раскладка расходов по статьям на настоящих выгрузках OZON.
 *
 * <p>Проверяется главное свойство: статьи покрывают расходы целиком. Если из
 * раскладки выпадет хотя бы один тип начисления, сумма статей перестанет совпадать
 * с {@link FinancialSummary#expenses()}, и в карточке расходов появится цифра,
 * которую невозможно проверить по детализации.
 */
class ExpenseBreakdownTest {

    private static final LocalDate D1 = LocalDate.of(2026, 4, 10);
    private static final String FIXTURE_D1 = "fixtures/accruals-2026-04-10.json";

    /** Ручной подсчёт по фикстуре 2026-04-10: все расходы, кроме комиссии и логистики. */
    private static final String EXPECTED_OTHER_D1 = "-3987.73";

    private static final LocalDate D2 = LocalDate.of(2026, 9, 26);
    private static final String FIXTURE_D2 = "fixtures/accruals-2026-09-26-full.json";

    @Test
    @DisplayName("Сумма статей равна расходам отчёта — на обоих днях")
    void groupsCoverAllExpenses() {
        assertCoversExpenses(FIXTURE_D1, D1);
        assertCoversExpenses(FIXTURE_D2, D2);
    }

    @Test
    @DisplayName("2026-04-10: каждая статья совпадает с ручным подсчётом")
    void day2026_04_10() {
        ExpenseBreakdown breakdown = breakdown(FIXTURE_D1, D1);

        assertThat(amountOf(breakdown, ExpenseCategory.COMMISSION)).isEqualByComparingTo("-12879.33");
        assertThat(amountOf(breakdown, ExpenseCategory.PROMOTION)).isEqualByComparingTo("-3059.40");
        assertThat(amountOf(breakdown, ExpenseCategory.LOGISTICS)).isEqualByComparingTo("-3000.71");
        assertThat(amountOf(breakdown, ExpenseCategory.STORAGE)).isEqualByComparingTo("-671.18");
        assertThat(amountOf(breakdown, ExpenseCategory.ACQUIRING)).isEqualByComparingTo("-171.68");
        assertThat(amountOf(breakdown, ExpenseCategory.OTHER)).isEqualByComparingTo("-85.47");
    }

    @Test
    @DisplayName("Статьи идут в порядке показа, а не в порядке появления в данных")
    void groupsFollowDisplayOrder() {
        ExpenseBreakdown breakdown = breakdown(FIXTURE_D1, D1);

        assertThat(breakdown.groups())
                .extracting(ExpenseGroup::category)
                .containsExactly(
                        ExpenseCategory.COMMISSION,
                        ExpenseCategory.PROMOTION,
                        ExpenseCategory.LOGISTICS,
                        ExpenseCategory.STORAGE,
                        ExpenseCategory.ACQUIRING,
                        ExpenseCategory.OTHER);
        assertThat(breakdown.groups())
                .extracting(ExpenseGroup::label)
                .containsExactly("Комиссия", "Продвижение", "Логистика",
                        "Хранение и обработка", "Эквайринг", "Прочие расходы");
    }

    @Test
    @DisplayName("Внутри статьи типы начислений идут от крупного к мелкому")
    void typesInsideGroupAreSortedByAmount() {
        ExpenseBreakdown breakdown = breakdown(FIXTURE_D1, D1);

        ExpenseGroup promotion = breakdown.group(ExpenseCategory.PROMOTION);
        assertThat(promotion).isNotNull();
        assertThat(promotion.items())
                .extracting(FinancialSummary.TypeAmount::name)
                .containsExactly("PayPerClick", "Promotion", "StarsMembership");
        assertThat(promotion.amount()).isEqualByComparingTo("-3059.40");
    }

    @Test
    @DisplayName("Комиссия — отдельная статья без типа начисления")
    void commissionIsItsOwnGroup() {
        ExpenseBreakdown breakdown = breakdown(FIXTURE_D1, D1);

        ExpenseGroup commission = breakdown.group(ExpenseCategory.COMMISSION);
        assertThat(commission).isNotNull();
        assertThat(commission.items()).hasSize(1);
        // Типа начисления у комиссии нет: она лежит в строке товара, а не отдельным
        // начислением. Клиент обязан показывать строку без номера типа.
        assertThat(commission.items().getFirst().typeId()).isNull();
        assertThat(commission.items().getFirst().name()).isEqualTo("Комиссия за продажу");
    }

    @Test
    @DisplayName("Логистика попадает в свою статью, а не в прочие расходы")
    void logisticsIsItsOwnGroup() {
        ExpenseBreakdown breakdown = breakdown(FIXTURE_D1, D1);

        ExpenseGroup logistics = breakdown.group(ExpenseCategory.LOGISTICS);
        assertThat(logistics).isNotNull();
        // Каталог услуг доставки у OZON открытый: статья определяется по источнику
        // начисления, а не по списку названий, иначе новый тип доставки молча уехал
        // бы в «Прочие».
        assertThat(logistics.items())
                .extracting(FinancialSummary.TypeAmount::name)
                .contains("Logistic", "ReturnFlowLogistic", "LastMileCourier");
    }

    @Test
    @DisplayName("Неизвестный тип начисления попадает в прочие расходы, а не пропадает")
    void unknownTypeFallsIntoOther() {
        // Пустой справочник — это худший случай: подписей нет вовсе, но расходы есть.
        ExpenseBreakdown breakdown = FinancialModel.expenseBreakdown(
                feesFrom(FIXTURE_D1, D1), new BigDecimal("-12879.33"), Map.of());

        assertThat(breakdown.group(ExpenseCategory.PROMOTION)).isNull();
        assertThat(breakdown.group(ExpenseCategory.ACQUIRING)).isNull();
        ExpenseGroup other = breakdown.group(ExpenseCategory.OTHER);
        assertThat(other).isNotNull();
        assertThat(other.items()).allSatisfy(item -> {
            assertThat(item.name()).startsWith("Тип ");
            assertThat(item.description()).isNull();
        });
        assertThat(other.amount()).isEqualByComparingTo(EXPECTED_OTHER_D1);
    }

    @Test
    @DisplayName("Расходов не было — статей нет вовсе, а не нули")
    void noExpensesNoGroups() {
        ExpenseBreakdown breakdown = FinancialModel.expenseBreakdown(List.of(), BigDecimal.ZERO, Map.of());

        assertThat(breakdown.groups()).isEmpty();
        assertThat(breakdown.total()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Возврат комиссии уменьшает статью, а не увеличивает расход")
    void refundedCommissionReducesGroup() {
        ExpenseBreakdown breakdown = FinancialModel.expenseBreakdown(
                List.of(), new BigDecimal("672.36"), Map.of());

        // Знак сохраняется как у OZON: возврат комиссии положителен и уменьшает расход.
        assertThat(amountOf(breakdown, ExpenseCategory.COMMISSION)).isEqualByComparingTo("672.36");
        assertThat(breakdown.total()).isEqualByComparingTo("672.36");
    }

    @Test
    @DisplayName("Нулевое начисление не превращается в статью с нулём")
    void zeroAmountsAreSkipped() {
        // OZON присылает нулевые услуги доставки: у них есть тип, но денег нет.
        // Строка «Logistic 0 ₽» в расшифровке читалась бы как ошибка.
        List<FeeFact> fees = List.of(
                new FeeFact(D1, "u-1", 1L, 100L, 32, BigDecimal.ZERO, FeeFact.FeeKind.DELIVERY),
                new FeeFact(D1, "u-1", 1L, 100L, 32, new BigDecimal("-100"), FeeFact.FeeKind.DELIVERY));

        ExpenseBreakdown breakdown = FinancialModel.expenseBreakdown(fees, null, labels());

        assertThat(breakdown.group(ExpenseCategory.LOGISTICS)).isNotNull();
        assertThat(breakdown.group(ExpenseCategory.LOGISTICS).items()).hasSize(1);
        assertThat(breakdown.total()).isEqualByComparingTo("-100");
    }

    @Test
    @DisplayName("Статья определяется по названию типа, а регистр не важен")
    void categoryIsChosenByNameIgnoringCase() {
        assertThat(ExpenseCategory.byTypeName("PayPerClick")).isEqualTo(ExpenseCategory.PROMOTION);
        assertThat(ExpenseCategory.byTypeName("payperclick")).isEqualTo(ExpenseCategory.PROMOTION);
        assertThat(ExpenseCategory.byTypeName("PremiumSubscription")).isEqualTo(ExpenseCategory.PROMOTION);
        assertThat(ExpenseCategory.byTypeName("ItemPacking")).isEqualTo(ExpenseCategory.STORAGE);
        assertThat(ExpenseCategory.byTypeName("SupplyInbound")).isEqualTo(ExpenseCategory.STORAGE);
        assertThat(ExpenseCategory.byTypeName("Acquiring")).isEqualTo(ExpenseCategory.ACQUIRING);
        assertThat(ExpenseCategory.byTypeName("Stencil")).isEqualTo(ExpenseCategory.OTHER);
        // Новый тип из обновлённого справочника не должен выпадать из отчёта.
        assertThat(ExpenseCategory.byTypeName("SomeNewOzonFee")).isEqualTo(ExpenseCategory.OTHER);
        assertThat(ExpenseCategory.byTypeName(null)).isEqualTo(ExpenseCategory.OTHER);
    }

    @Test
    @DisplayName("Все статьи имеют подпись для интерфейса")
    void everyCategoryHasLabel() {
        assertThat(ExpenseCategory.displayOrder())
                .containsExactly(
                        ExpenseCategory.COMMISSION,
                        ExpenseCategory.PROMOTION,
                        ExpenseCategory.LOGISTICS,
                        ExpenseCategory.STORAGE,
                        ExpenseCategory.ACQUIRING,
                        ExpenseCategory.OTHER);
        assertThat(ExpenseCategory.displayOrder())
                .allSatisfy(category -> assertThat(category.label()).isNotBlank());
        assertThat(ExpenseCategory.STORAGE.label()).isEqualTo("Хранение и обработка");
    }

    // ---------------------------------------------------------------- helpers

    private static void assertCoversExpenses(String fixture, LocalDate date) {
        List<ProductFact> products = productsFrom(fixture, date);
        List<FeeFact> fees = feesFrom(fixture, date);
        FinancialSummary summary = FinancialModel.summarize(date, date, products, fees, Map.of());
        ExpenseBreakdown breakdown = FinancialModel.expenseBreakdown(
                fees, summary.commission(), labels());

        // expenses() берёт модули статей, а раскладка хранит знак — сравниваем по модулю.
        assertThat(breakdown.total().abs())
                .as("сумма статей равна расходам отчёта за %s", date)
                .isEqualByComparingTo(summary.expenses());
        assertThat(breakdown.total()).isNegative();

        BigDecimal sumOfGroups = breakdown.groups().stream()
                .map(ExpenseGroup::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumOfGroups).isEqualByComparingTo(breakdown.total());
    }

    private static BigDecimal amountOf(ExpenseBreakdown breakdown, ExpenseCategory category) {
        ExpenseGroup group = breakdown.group(category);
        assertThat(group).as("статья %s", category).isNotNull();
        return group.amount();
    }

    private static ExpenseBreakdown breakdown(String fixture, LocalDate date) {
        List<FeeFact> fees = feesFrom(fixture, date);
        FinancialSummary summary = FinancialModel.summarize(
                date, date, productsFrom(fixture, date), fees, Map.of());
        return FinancialModel.expenseBreakdown(fees, summary.commission(), labels());
    }

    private static Map<Integer, FinancialModel.TypeLabels> labels() {
        Map<Integer, FinancialModel.TypeLabels> labels = new LinkedHashMap<>();
        labels.put(41, new FinancialModel.TypeLabels("PayPerClick", "Оплата за клик"));
        labels.put(54, new FinancialModel.TypeLabels("Promotion", "Продвижение товара"));
        labels.put(74, new FinancialModel.TypeLabels("StarsMembership", "Звёздные товары"));
        labels.put(46, new FinancialModel.TypeLabels("Placements", "Размещение товаров на складах Ozon"));
        labels.put(12, new FinancialModel.TypeLabels("CrossDock", "Кросс-докинг"));
        labels.put(84, new FinancialModel.TypeLabels("ItemPacking", "Дополнительная упаковка на складе Ozon"));
        labels.put(77, new FinancialModel.TypeLabels("SupplyInbound", "Обработка товара"));
        labels.put(1, new FinancialModel.TypeLabels("Acquiring", "Эквайринг"));
        labels.put(48, new FinancialModel.TypeLabels("PremiumCashbackIndividualPoints", "Бонусы продавца"));
        labels.put(39, new FinancialModel.TypeLabels("PackingFee", "Упаковка товара партнёрами"));
        labels.put(38, new FinancialModel.TypeLabels("PackageCost", "Обеспечение материалами"));
        labels.put(76, new FinancialModel.TypeLabels("StockInsurance", "Страхование товара"));
        labels.put(6, new FinancialModel.TypeLabels("Logistic", "Логистика"));
        labels.put(59, new FinancialModel.TypeLabels("ReturnFlowLogistic", "Обратная логистика"));
        labels.put(29, new FinancialModel.TypeLabels("LastMileCourier", "Доставка до места выдачи"));
        labels.put(17, new FinancialModel.TypeLabels("Drop-Off Agent", "Drop-off партнёрами"));
        labels.put(16, new FinancialModel.TypeLabels("Drop-Off", "Обработка Drop-off"));
        labels.put(98, new FinancialModel.TypeLabels(
                "DeliveryToHandoverPlaceByOzon", "Доставка до места выдачи силами Ozon"));
        labels.put(45, new FinancialModel.TypeLabels(
                "PickUpPointReturnAcceptance", "Обработка возвратов"));
        labels.put(7, new FinancialModel.TypeLabels("Cancellation", "Обработка отменённых"));
        labels.put(32, new FinancialModel.TypeLabels("Logistic", "Логистика"));
        return labels;
    }

    /** Превращает сохранённый ответ OZON в факты ровно так же, как это делает запрос к БД. */
    private static List<ProductFact> productsFrom(String fixture, LocalDate date) {
        List<ProductFact> result = new ArrayList<>();
        for (var accrual : OzonTestFixturesAccess.parse(fixture)) {
            if (accrual.posting() == null) {
                continue;
            }
            for (var product : accrual.posting().products()) {
                var commission = product.commission();
                result.add(new ProductFact(date, accrual.unitNumber(), accrual.externalId(),
                        product.sku(), product.quantity(),
                        commission == null ? null : commission.salePrice(),
                        commission == null ? null : commission.saleCommission(),
                        commission == null ? null : commission.bonus(),
                        commission == null ? null : commission.coinvestment()));
            }
        }
        return result;
    }

    private static List<FeeFact> feesFrom(String fixture, LocalDate date) {
        List<FeeFact> result = new ArrayList<>();
        for (var accrual : OzonTestFixturesAccess.parse(fixture)) {
            if (accrual.posting() != null) {
                for (var product : accrual.posting().products()) {
                    for (var service : product.deliveryServices()) {
                        result.add(new FeeFact(date, accrual.unitNumber(), accrual.externalId(),
                                product.sku(), service.typeId(), service.amount(),
                                FeeFact.FeeKind.DELIVERY));
                    }
                }
            }
            if (accrual.itemFees() != null) {
                for (var itemFee : accrual.itemFees()) {
                    for (var detail : itemFee.fees()) {
                        result.add(new FeeFact(date, accrual.unitNumber(), accrual.externalId(),
                                itemFee.sku(), detail.typeId(), detail.amount(),
                                FeeFact.FeeKind.ITEM));
                    }
                }
            }
            if (accrual.nonItemFee() != null) {
                result.add(new FeeFact(date, accrual.unitNumber(), accrual.externalId(),
                        null, accrual.nonItemFee().typeId(), accrual.nonItemFee().amount(),
                        FeeFact.FeeKind.NON_ITEM));
            }
            if (accrual.containerFees() != null) {
                for (var fee : accrual.containerFees()) {
                    result.add(new FeeFact(date, accrual.unitNumber(), accrual.externalId(),
                            null, fee.typeId(), fee.amount(),
                            FeeFact.FeeKind.CONTAINER));
                }
            }
        }
        return result;
    }
}
