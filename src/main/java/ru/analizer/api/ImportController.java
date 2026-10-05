package ru.analizer.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.analizer.persistence.AccountLookup;
import ru.analizer.persistence.entity.SellerAccount;
import ru.analizer.sync.ImportProgress;
import ru.analizer.sync.ImportService;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;


/**
 * Фоновые импорты данных маркетплейса.
 *
 * <p>Единственная группа методов с побочными эффектами: она ходит в API маркетплейса и
 * пишет в базу. Все остальные методы API только читают нашу базу, поэтому вызвать их
 * можно сколько угодно раз, не потратив ни одной квоты.
 *
 * <p>Загрузка всегда фоновая. Месяц — это десятки обращений к OZON, год — сотни; если
 * делать это внутри HTTP-запроса, клиент отвалится по таймауту, хотя данные наполовину
 * загрузятся. Поэтому POST сразу возвращает номер прогона, а работа продолжается после
 * ответа.
 */
@RestController
@RequestMapping("/api/marketplaces/{marketplace}/imports")
public class ImportController {

    private final ImportService importService;
    private final AccountLookup accountLookup;

    public ImportController(ImportService importService, AccountLookup accountLookup) {
        this.importService = importService;
        this.accountLookup = accountLookup;
    }

    /**
     * POST /api/marketplaces/ozon/imports/finance
     *
     * <p>Запускает импорт начислений за период и сразу возвращает прогресс.
     *
     * <p>Догружаются только недостающие дни: повторный запуск не создаёт дублей и не
     * ходит в OZON зря. Уже загруженные окончательные дни не запрашиваются заново.
     *
     * @param dateFrom           начало периода включительно
     * @param dateTo             конец периода включительно
     * @param refreshAccrualTypes обновить ли справочник типов начислений; стоит одного
     *                           дополнительного запроса к OZON
     * @return 202 Accepted и номер прогона
     * @throws ru.analizer.api.NotConnectedException 409, если маркетплейс не подключён
     * @throws ru.analizer.sync.ImportConflictException 409, если уже идёт импорт
     *                                              пересекающегося периода
     */
    @PostMapping("/finance")
    public ResponseEntity<ImportProgress> startFinanceImport(
            @PathVariable String marketplace,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "false") boolean refreshAccrualTypes) {

        // Импорту нужны реквизиты, а они появляются только при подключении.
        // Раньше здесь был ensureAccount, который создавал аккаунт с ключами из
        // конфигурации; теперь ключи ввод��т пользователь, и заводить аккаунт
        // в обход проверки нельзя — иначе импорт стартовал бы с чужими ключами.
        SellerAccount account = accountLookup.requireAccount(marketplace);
        ImportProgress progress = importService.startFinanceImport(
                account, marketplaceCode(marketplace), dateFrom, dateTo, refreshAccrualTypes);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(progress);
    }

    /**
     * POST /api/marketplaces/ozon/imports/catalog
     *
     * <p>Запускает импорт характеристик товаров: названий, ISBN, авторов. Периода у
     * каталога нет, поэтому дат в запросе не бывает.
     *
     * <p>Каталог каждый раз переписывается целиком: OZON не сообщает, какие товары
     * изменились, и выборочный перебор стоил бы столько же запросов.
     *
     * @return 202 Accepted и номер прогона
     * @throws ru.analizer.sync.ImportConflictException 409, если импорт каталога уже идёт
     */
    @PostMapping("/catalog")
    public ResponseEntity<ImportProgress> startCatalogImport(@PathVariable String marketplace) {
        SellerAccount account = accountLookup.requireAccount(marketplace);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(importService.startCatalogImport(account, marketplaceCode(marketplace)));
    }

    /**
     * GET /api/marketplaces/ozon/imports
     *
     * <p>Последние прогоны, новые сверху. Всегда массив, даже если прогонов ещё не было,
     * — чтобы клиенту не приходилось различать пустой список и {@code null}.
     *
     * <p>Запрос не фильтрует по виду импорта: клиенту полезно видеть и финансовые, и
     * импорты каталога в одной истории.
     */
    @GetMapping
    public List<ImportProgress> listImports(@PathVariable String marketplace) {
        SellerAccount account = accountLookup.requireAccount(marketplace);
        return importService.recentFor(account.getId());
    }

    /**
     * GET /api/marketplaces/ozon/imports/{importId}
     *
     * <p>Прогресс одного прогона. Опросяте этот метод, пока {@code inProgress} не станет
     * {@code false}, чтобы показать полосу прогресса.
     *
     * @return 200 с прогрессом либо 404, если прогона с таким номером нет
     */
    @GetMapping("/{importId}")
    public ResponseEntity<ImportProgress> getImport(@PathVariable String marketplace,
                                                    @PathVariable Long importId) {
        SellerAccount account = accountLookup.requireAccount(marketplace);
        return importService.progressOf(importId, account.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    /** Код маркетплейса в верхнем регистре — так он хранится и возвращается в ответе. */
    private String marketplaceCode(String marketplace) {
        return marketplace.trim().toUpperCase(java.util.Locale.ROOT);
    }
}