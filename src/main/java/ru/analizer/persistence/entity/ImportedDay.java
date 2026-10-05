package ru.analizer.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Учёт синхронизации одного дня.
 *
 * <p>Нужен, чтобы отличать «день загружен и начислений не было» от «день не загружен»,
 * и чтобы знать, какие дни периода осталось догрузить.
 */
@Entity
@Table(name = "imported_day")
public class ImportedDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_account_id", nullable = false)
    private SellerAccount sellerAccount;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DayStatus status = DayStatus.PENDING;

    /**
     * Данные дня окончательны, то есть OZON уже не изменит их.
     * Иначе отчёт за период с сегодняшним днём покажет заниженные цифры.
     */
    @Column(name = "is_final", nullable = false)
    private boolean finalDay;

    @Column(name = "accrual_count", nullable = false)
    private int accrualCount;

    @Column(name = "total_amount", precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "first_synced_at")
    private Instant firstSyncedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    /** Момент, с которого повторные загрузки перестали менять сумму дня. */
    @Column(name = "unchanged_since")
    private Instant unchangedSince;

    /**
     * Сколько раз повторная загрузка изменила сумму. Это и есть измерение «зрелости»:
     * система сама накапливает факты вместо того, чтобы угадывать окно.
     */
    @Column(name = "change_count", nullable = false)
    private int changeCount;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected ImportedDay() {
    }

    public ImportedDay(SellerAccount sellerAccount, LocalDate day) {
        this.sellerAccount = sellerAccount;
        this.day = day;
        this.status = DayStatus.PENDING;
    }

    /**
     * Фиксирует результат загрузки дня.
     *
     * @return {@code true}, если данные за этот день изменились относительно прошлой
     *         загрузки. Именно это наблюдение копим, чтобы оценить окно зрелости.
     */
    public boolean recordSyncResult(BigDecimal newTotalAmount, int accrualCount, Instant now) {
        boolean changed = firstSyncedAt != null
                && (accrualCount != this.accrualCount || !sameAmount(newTotalAmount, this.totalAmount));

        if (changed) {
            this.changeCount++;
            this.unchangedSince = null;
        } else if (this.unchangedSince == null) {
            this.unchangedSince = now;
        }

        if (this.firstSyncedAt == null) {
            this.firstSyncedAt = now;
        }
        this.lastSyncedAt = now;
        this.accrualCount = accrualCount;
        this.totalAmount = newTotalAmount;
        this.status = DayStatus.DONE;
        this.lastError = null;
        return changed;
    }

    public void markFailed(String error, Instant now) {
        this.status = DayStatus.FAILED;
        this.lastError = error == null || error.length() > 2000 ? abbreviate(error) : error;
        this.lastSyncedAt = now;
    }

    public void markInProgress() {
        this.status = DayStatus.IN_PROGRESS;
    }

    /**
     * Пересчитывает признак окончательности.
     *
     * @param today            текущая дата
     * @param maturityInDays   окно зрелости: день окончателен, только если он старше него
     */
    public void refreshFinality(LocalDate today, int maturityInDays, boolean dataProvenStable) {
        LocalDate newestFinal = today.minusDays(maturityInDays);
        this.finalDay = !day.isAfter(newestFinal) && dataProvenStable;
    }

    /**
     * Данные считаются доказанно стабильными, если повторная загрузка их не изменила,
     * либо если день заведомо старше окна зрелости (тогда отсутствие изменений проверить
     * уже нельзя — слишком много пройдёт времени).
     */
    public boolean dataProvenStable(int maturityInDays, LocalDate today) {
        if (status != DayStatus.DONE) {
            return false;
        }
        boolean olderThanWindow = !day.isAfter(today.minusDays(maturityInDays + 1));
        return unchangedSince != null || olderThanWindow;
    }

    private static boolean sameAmount(BigDecimal first, BigDecimal second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.compareTo(second) == 0;
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    public Long getId() {
        return id;
    }

    public SellerAccount getSellerAccount() {
        return sellerAccount;
    }

    public LocalDate getDay() {
        return day;
    }

    public DayStatus getStatus() {
        return status;
    }

    public boolean isFinalDay() {
        return finalDay;
    }

    public int getAccrualCount() {
        return accrualCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getFirstSyncedAt() {
        return firstSyncedAt;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public Instant getUnchangedSince() {
        return unchangedSince;
    }

    public int getChangeCount() {
        return changeCount;
    }

    public String getLastError() {
        return lastError;
    }
}