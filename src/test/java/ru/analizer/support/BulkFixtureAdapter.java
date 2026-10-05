package ru.analizer.support;

import ru.analizer.integration.MarketplaceAdapter;
import ru.analizer.integration.model.AccrualDto;
import ru.analizer.integration.model.AccrualTypeInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Адаптер, который выдаёт правдоподобные данные за любую дату.
 *
 * <p>Нужен для проверки загрузки длинных периодов: загружать полгода из настоящего OZON
 * нельзя — это 180+ обращений к чужому API. Здесь данные синтетические, но по форме
 * неотличимы от настоящих: те же категории, те же поля, те же знаки.
 *
 * <p>Каждый день содержит несколько операций POSTING, ITEM и NON_ITEM, чтобы покрытие
 * и аналитика считались так же, как на живых данных.
 */
public class BulkFixtureAdapter implements MarketplaceAdapter {

    /** Операций POSTING в день. */
    private static final int POSTINGS_PER_DAY = 2;
    /** Операций ITEM в день. */
    private static final int ITEMS_PER_DAY = 2;
    /** Операций NON_ITEM в день. */
    private static final int NON_ITEMS_PER_DAY = 1;

    private static final String UNIT_PREFIX = "TEST-";

    /** Идентификаторы операций должны выглядеть правдоподобно и быть стабильными по дате. */
    private final AtomicLong idGenerator = new AtomicLong(60_000_000_000L);
    private final AtomicInteger daysRequested = new AtomicInteger();
    private final ConcurrentHashMap<LocalDate, List<AccrualDto>> data = new ConcurrentHashMap<>();

    /** Искусственная задержка на день, чтобы успевать проверять прогресс. */
    private volatile long delayPerDayMillis = 0;

    @Override
    public String marketplaceCode() {
        return "OZON";
    }

        @Override
    // Реквизиты в тестах не проверяются: адаптер подменён заглушкой.
    public void verifyCredentials(ru.analizer.account.domain.MarketplaceCredentials credentials) {
    }

    @Override
    public List<AccrualTypeInfo> fetchAccrualTypes(ru.analizer.account.domain.MarketplaceCredentials credentials) {
        // Тот же реальный справочник, что и в остальных тестах.
        return FixtureAdapters.types();
    }

    @Override
    public List<AccrualDto> fetchAccrualsByDay(ru.analizer.account.domain.MarketplaceCredentials credentials, LocalDate date) {
        daysRequested.incrementAndGet();
        List<AccrualDto> result = data.computeIfAbsent(date, BulkFixtureAdapter::generate);
        if (delayPerDayMillis > 0) {
            try {
                Thread.sleep(delayPerDayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return result;
    }

    /** Сколько дней реально запросили у адаптера. */
    public int daysRequested() {
        return daysRequested.get();
    }

    public long totalAccrualsForDays(LocalDate from, LocalDate to) {
        long total = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            total += generate(date).size();
        }
        return total;
    }

    public void reset() {
        data.clear();
        daysRequested.set(0);
        delayPerDayMillis = 0;
    }

    public void setDelayPerDayMillis(long millis) {
        this.delayPerDayMillis = millis;
    }

    private static List<AccrualDto> generate(LocalDate date) {
        List<AccrualDto> accruals = new ArrayList<>();
        long seed = date.toEpochDay();

        for (int i = 0; i < POSTINGS_PER_DAY; i++) {
            long sku = 100_000_000L + (seed * 10 + i);
            BigDecimal salePrice = new BigDecimal("500.00").add(BigDecimal.valueOf(seed % 97));
            BigDecimal bonus = new BigDecimal("200.00");
            BigDecimal coinvestment = new BigDecimal("5.00");
            BigDecimal commission = salePrice.negate().multiply(new BigDecimal("0.43"));
            BigDecimal logistics = new BigDecimal("-40.00");

            List<AccrualDto.FeeDetail> services = List.of(
                    new AccrualDto.FeeDetail(32, logistics, "RUB"),
                    new AccrualDto.FeeDetail(29, new BigDecimal("-8.02"), "RUB"));

            List<AccrualDto.Product> products = List.of(new AccrualDto.Product(
                    sku, 1,
                    new AccrualDto.Commission(
                            salePrice.add(bonus).add(coinvestment),   // seller_price
                            salePrice,
                            salePrice.add(bonus).add(coinvestment),   // sale_amount
                            commission,
                            commission,
                            "0.430000",
                            coinvestment,
                            bonus,
                            "RUB"),
                    logistics.add(new BigDecimal("-8.02")), "RUB", services));

            accruals.add(new AccrualDto(
                    60_000_000_000L + seed * 100 + i, date,
                    UNIT_PREFIX + date + "-" + i,
                    AccrualDto.Category.POSTING, null,
                    salePrice.add(bonus).add(coinvestment).add(commission).add(logistics).add(new BigDecimal("-8.02")),
                    "RUB",
                    new AccrualDto.Posting("Fbs", null, products),
                    null, null, null, raw(date)));
        }

        for (int i = 0; i < ITEMS_PER_DAY; i++) {
            long sku = 100_000_000L + (seed * 10 + i);
            BigDecimal amount = new BigDecimal("-12.50");
            accruals.add(new AccrualDto(
                    60_000_000_000L + seed * 100 + 50 + i, date,
                    UNIT_PREFIX + date + "-" + i,
                    AccrualDto.Category.ITEM, null,
                    amount, "RUB", null,
                    List.of(new AccrualDto.ItemFee(sku, 1,
                            List.of(new AccrualDto.FeeDetail(74, amount, "RUB")))),
                    null, null, raw(date)));
        }

        for (int i = 0; i < NON_ITEMS_PER_DAY; i++) {
            BigDecimal amount = new BigDecimal("-100.00");
            accruals.add(new AccrualDto(
                    60_000_000_000L + seed * 100 + 80 + i, date, null,
                    AccrualDto.Category.NON_ITEM, null,
                    amount, "RUB", null, null,
                    new AccrualDto.FeeDetail(41, amount, "RUB"), null, raw(date)));
        }

        return List.copyOf(accruals);
    }

    private static String raw(LocalDate date) {
        return "{\"synthetic\":true,\"date\":\"" + date + "\"}";
    }
}