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
 * Фоновая задача загрузки периода.
 *
 * <p>Существует, чтобы отчёт мог сказать «данные подгружаются, вот 12 из 30 дней»,
 * а после перезапуска приложения — продолжить с места остановки.
 */
@Entity
@Table(name = "sync_job")
public class SyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_account_id", nullable = false)
    private SellerAccount sellerAccount;

    @Column(name = "marketplace_code", nullable = false, length = 64)
    private String marketplaceCode;

    @Column(name = "date_from")
    private LocalDate dateFrom;

    @Column(name = "date_to")
    private LocalDate dateTo;

    /**
     * Вид задачи. У финансовой есть период, у загрузки каталога дат нет вовсе,
     * поэтому вид задан явно, а не выводится из наличия дат.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 16)
    private JobType jobType = JobType.FINANCE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    @Column(name = "done_days", nullable = false)
    private int doneDays;

    @Column(name = "skipped_days", nullable = false)
    private int skippedDays;

    @Column(name = "failed_days", nullable = false)
    private int failedDays;

    @Column(name = "current_day")
    private LocalDate currentDay;

    @Column(name = "error", columnDefinition = "text")
    private String error;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected SyncJob() {
    }

    public SyncJob(SellerAccount sellerAccount, String marketplaceCode,
                   LocalDate dateFrom, LocalDate dateTo, int totalDays) {
        this(sellerAccount, marketplaceCode, JobType.FINANCE, dateFrom, dateTo, totalDays);
    }

    public SyncJob(SellerAccount sellerAccount, String marketplaceCode, JobType jobType,
                   LocalDate dateFrom, LocalDate dateTo, int totalDays) {
        this.sellerAccount = sellerAccount;
        this.marketplaceCode = marketplaceCode;
        this.jobType = jobType == null ? JobType.FINANCE : jobType;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.totalDays = totalDays;
    }

    public void start(Instant now) {
        this.status = JobStatus.RUNNING;
        this.startedAt = now;
    }

    public void currentDay(LocalDate day) {
        this.currentDay = day;
    }

    /**
     * Общее число единиц работы: дней у финансовой задачи, товаров у задачи каталога.
     *
     * <p>Раздельной колонки для товаров не заводим: показывать прогресс нужно в том же
     * поле, а вид задачи и так известен по {@link #jobType}.
     */
    public void setTotal(int total) {
        this.totalDays = total;
    }

    public void dayDone() {
        this.doneDays++;
    }

    public void daySkipped() {
        this.skippedDays++;
    }

    public void dayFailed() {
        this.failedDays++;
    }

    public void finish(Instant now) {
        this.status = failedDays > 0 ? JobStatus.FAILED : JobStatus.DONE;
        this.finishedAt = now;
    }

    public void fail(String reason, Instant now) {
        this.status = JobStatus.FAILED;
        this.error = reason == null || reason.length() > 2000 ? abbreviate(reason) : reason;
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

    public JobType getJobType() {
        return jobType;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getTotalDays() {
        return totalDays;
    }

    public int getDoneDays() {
        return doneDays;
    }

    public int getSkippedDays() {
        return skippedDays;
    }

    public int getFailedDays() {
        return failedDays;
    }

    public LocalDate getCurrentDay() {
        return currentDay;
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