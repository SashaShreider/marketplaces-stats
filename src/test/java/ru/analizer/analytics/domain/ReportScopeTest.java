package ru.analizer.analytics.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Отбор, которым ограничен отчёт.
 *
 * <p>Проверяется не форма подписи, а поведение: какие строки попадают в отчёт, что
 * происходит с начислениями без товара и почему товары сильнее автора.
 */
class ReportScopeTest {

    private static final LocalDate D = LocalDate.of(2026, 9, 1);

    @Test
    @DisplayName("Без отбора отчёт не ограничен")
    void allScopeAcceptsEverything() {
        ReportScope scope = ReportScope.all();

        assertThat(scope.scoped()).isFalse();
        assertThat(scope.accepts(100L, null)).isTrue();
        // Начисления без SKU при этом тоже входят: отчёт по всему магазину coverит всё.
        assertThat(scope.acceptsFee(fee(null), null)).isTrue();
        assertThat(scope.label()).isEqualTo("Все товары");
    }

    @Test
    @DisplayName("Отбор по товарам отсекает чужие товары и все начисления без SKU")
    void skusScopeRejectsForeignSkusAndUnallocatedFees() {
        ReportScope scope = ReportScope.ofSkus(List.of(100L, 200L));

        assertThat(scope.scoped()).isTrue();
        assertThat(scope.accepts(100L, null)).isTrue();
        assertThat(scope.accepts(200L, null)).isTrue();
        assertThat(scope.accepts(300L, null)).isFalse();
        // Расход без товара распределить нечем: выдуманное правило исказило бы прибыль.
        assertThat(scope.acceptsFee(fee(null), null)).isFalse();
        assertThat(scope.acceptsFee(fee(100L), null)).isTrue();
    }

    @Test
    @DisplayName("Отбор по автору идёт по множеству его товаров, а не по имени в каждой строке")
    void authorScopeUsesResolvedSkuSet() {
        ReportScope scope = ReportScope.ofAuthor("Сурцуков А.");

        assertThat(scope.scoped()).isTrue();
        assertThat(scope.accepts(100L, Set.of(100L, 555L))).isTrue();
        assertThat(scope.accepts(200L, Set.of(100L, 555L))).isFalse();
        // Автор есть, а товаров у него нет: отчёт пустой, а не «весь магазин».
        assertThat(scope.accepts(200L, Set.of())).isFalse();
        assertThat(scope.label()).isEqualTo("Сурцуков А.");
    }

    @Test
    @DisplayName("Товары сильнее автора: выбрав товар, пользователь уже ответил, чей он")
    void skusWinOverAuthor() {
        ReportScope scope = ReportScope.ofSkus(List.of(100L));

        // Даже если автор передан вместе с товарами, применяется отбор по товарам.
        assertThat(scope.author()).isNull();
        assertThat(scope.accepts(100L, null)).isTrue();
        assertThat(scope.accepts(300L, Set.of(300L))).isFalse();
    }

    @Test
    @DisplayName("Пустые значения означают «без фильтра», а не «ничего не найдено»")
    void blankValuesMeanNoFilter() {
        assertThat(ReportScope.ofAuthor(null).scoped()).isFalse();
        assertThat(ReportScope.ofAuthor("   ").scoped()).isFalse();
        assertThat(ReportScope.ofSkus(null).scoped()).isFalse();
        assertThat(ReportScope.ofSkus(List.of()).scoped()).isFalse();
        // null в списке не должен превращаться в отбор по несуществующему товару.
        assertThat(ReportScope.ofSkus(java.util.Arrays.asList(100L, null)).skus())
                .containsExactly(100L);
    }

    @Test
    @DisplayName("Длинный список товаров не перечисляется в подписи целиком")
    void longSkuListIsShortenedInLabel() {
        assertThat(ReportScope.ofSkus(List.of(1L, 2L)).label()).isEqualTo("SKU 1, 2");
        assertThat(ReportScope.ofSkus(List.of(1L, 2L, 3L)).label()).isEqualTo("SKU 1, 2, 3");
        assertThat(ReportScope.ofSkus(List.of(1L, 2L, 3L, 4L, 5L)).label())
                .as("в шапке отчёта длинный список занял бы всю строку")
                .isEqualTo("SKU 1, 2, 3 и ещё 2");
    }

    private static FeeFact fee(Long sku) {
        return new FeeFact(D, "u-1", 1L, sku, 41, new BigDecimal("-100"),
                sku == null ? FeeFact.FeeKind.NON_ITEM : FeeFact.FeeKind.ITEM);
    }
}
