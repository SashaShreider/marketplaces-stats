package ru.analizer.integration;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Небольшая обёртка, чтобы сравнения сумм читались в тестах как {@code sum.is("11297.23")}.
 */
public final class BigDecimalAssert {

    private final BigDecimal actual;

    public BigDecimalAssert(BigDecimal actual) {
        this.actual = actual;
    }

    public void is(String expected) {
        assertThat(actual).isEqualByComparingTo(new BigDecimal(expected));
    }

    public void isNegative() {
        assertThat(actual.signum()).isNegative();
    }

    public void isZero() {
        assertThat(actual.signum()).isZero();
    }
}
