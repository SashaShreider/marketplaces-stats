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

import java.time.Instant;
import java.time.LocalDate;

/**
 * Фоновый импорт данных маркетплейса.
 *
 * <p>Существует, чтобы отчёт мог сказать «данные подгружаются, вот 12 из 30»,
 * а после перезапуска приложения — продолжить с места остановки.
 *
 * <p>Счётчики названы в единицах работы, а не в днях: у финансового импорта единица —
 * день, у импорта каталога — товар. Что именно считается, показывает
 * {@link #importType}.
 */
@Entity
@Table(name = "import_run")
public class ImportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_account_id", nullable = false)
    private SellerAccount sellerAccount;

    @Column(name = "marketplace_code", nullable = false, length = 64)
    private String marketplaceCode;

    /** Период финансового импорта. У импорта каталога дат нет, поэтому поля nullable. */
    @Column(name = "date_from")
    private LocalDate dateFrom;

    @Column(name = "date_to")
    private LocalDate dateTo;

    /**
     * Что импортируется. Задано явно, а не выводится из наличия дат: отсутствие
     * дат может означать и ошибку, и каталог.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "import_type", nullable = false, length = 16)
    private ImportType importType = ImportType.FINANCE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RunState status = RunState.PENDING;

    @Column(name = "total_units", nullable = false)
    private int totalUnits;

    @Column(name = "done_units", nullable = false)
    private int doneUnits;

    @Column(name = "skipped_units", nullable = false)
    private int skippedUnits;

    @Column(name = "failed_units", nullable = false)
    private int failedUnits;

    /** Дата в работе или SKU — по {@link #importType}. */
    @Column(name = "current_unit")
    private String currentUnit;

    @Column(name = "error", columnDefinition = "text")
    private String error;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected ImportRun() {
    }

    public ImportRun(SellerAccount sellerAccount, String marketplaceCode, ImportType importType,
                     LocalDate dateFrom, LocalDate dateTo, int totalUnits) {
        this.sellerAccount = sellerAccount;
        this.marketplaceCode = marketplaceCode;
        this.importType = importType == null ? ImportType.FINANCE : importType;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.totalUnits = totalUnits;
    }

    public void start(Instant now) {
        this.status = RunState.RUNNING;
        this.startedAt = now;
    }

    /**
     * Единица работы в обработке: дата для финансового импорта, SKU для каталога.
     */
    public void currentUnit(String unit) {
        this.currentUnit = unit;
    }

    public void setTotalUnits(int totalUnits) {
        this.totalUnits = totalUnits;
    }

    public void unitDone() {
        this.doneUnits++;
    }

    public void unitSkipped() {
        this.skippedUnits++;
    }

    public void unitFailed() {
        this.failedUnits++;
    }

    public void finish(Instant now) {
        this.status = failedUnits > 0 ? RunState.FAILED : RunState.DONE;
        this.finishedAt = now;
    }

    public void fail(String reason, Instant now) {
        this.status = RunState.FAILED;
        this.error = abbreviate(reason);
        this.finishedAt = now;
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

    public String getMarketplaceCode() {
        return marketplaceCode;
    }

    public ImportType getImportType() {
        return importType;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public RunState getStatus() {
        return status;
    }

    public int getTotalUnits() {
        return totalUnits;
    }

    public int getDoneUnits() {
        return doneUnits;
    }

    public int getSkippedUnits() {
        return skippedUnits;
    }

    public int getFailedUnits() {
        return failedUnits;
    }

    public String getCurrentUnit() {
        return currentUnit;
    }

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}