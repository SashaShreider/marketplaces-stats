package ru.analizer.sync.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.analizer.account.domain.AccountLookup;
import ru.analizer.account.domain.SellerAccount;
import ru.analizer.sync.application.ImportService;
import ru.analizer.sync.domain.ImportProgress;

import java.time.LocalDate;
import java.util.List;

/**
 * Фоновые импорты данных одного магазина.
 *
 * <p>Единственная группа методов с побочными эффектами: она ходит в API маркетплейса и
 * пишет в базу. Все остальные методы API только читают нашу базу, поэтому вызвать их
 * можно сколько угодно раз, не потратив ни одной квоты.
 *
 * <p>Загрузка всегда фоновая. Месяц — это десятки обращений к OZON, год — сотни; если
 * делать это внутри HTTP-запроса, клиент отвалится по таймауту, хотя данные наполовину
 * загрузятся. Поэтому POST сразу возвращает номер прогона, а работа продолжается после
 * ответа.
 *
 * <p>Прогон относится ровно к одному магазину, и проверка пересечения периодов тоже
 * ведётся по магазину: два кабинета одного продавца импортируются независимо и не
 * мешают друг другу.
 */
@RestController
@RequestMapping("/api/accounts/{accountId}/imports")
public class ImportController {

    private final ImportService importService;
    private final AccountLookup accountLookup;

    public ImportController(ImportService importService, AccountLookup accountLookup) {
        this.importService = importService;
        this.accountLookup = accountLookup;
    }

    /**
     * POST /api/accounts/1/imports/finance
     *
     * <p>Запускает импорт начислений за период и сразу возвращает прогресс.
     *
     * <p>Догружаются только недостающие дни: повторный запуск не создаёт дублей и не
     * ходит в OZON зря. Уже загруженные окончательные дни не запрашиваются заново.
     *
     * @param dateFrom             начало периода включительно
     * @param dateTo               конец периода включительно
     * @param refreshAccrualTypes обновить ли справочник типов начислений; стоит одного
     *                             дополнительного запроса к OZON
     * @return 202 Accepted и номер прогона
     * @throws ru.analizer.web.AccountNotFoundException магазина нет или он не этот
     * @throws ru.analizer.sync.domain.ImportConflictException 409, если уже идёт импорт
     *                                              пересекающегося периода
     */
    @PostMapping("/finance")
    public ResponseEntity<ImportProgress> startFinanceImport(
            @PathVariable Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "false") boolean refreshAccrualTypes) {

        // Реквизиты берутся у подключённого магазина. Заводить магазин отсюда нельзя:
        // ключи вводит пользователь, и импорт в обход проверки стартовал бы с чужими.
        SellerAccount account = accountLookup.requireAccount(accountId);
        ImportProgress progress = importService.startFinanceImport(
                account, account.getMarketplace().getCode(), dateFrom, dateTo, refreshAccrualTypes);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(progress);
    }

    /**
     * POST /api/accounts/1/imports/catalog
     *
     * <p>Запускает импорт характеристик товаров: названий, ISBN, авторов. Периода у
     * каталога нет, поэтому дат в запросе не бывает.
     *
     * <p>Каталог каждый раз переписывается целиком: OZON не сообщает, какие товары
     * изменились, и выборочный перебор стоил бы столько же запросов.
     *
     * @return 202 Accepted и номер прогона
     * @throws ru.analizer.web.AccountNotFoundException магазина нет или он не этот
     * @throws ru.analizer.sync.domain.ImportConflictException 409, если импорт каталога уже идёт
     */
    @PostMapping("/catalog")
    public ResponseEntity<ImportProgress> startCatalogImport(@PathVariable Long accountId) {
        SellerAccount account = accountLookup.requireAccount(accountId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(importService.startCatalogImport(account, account.getMarketplace().getCode()));
    }

    /**
     * GET /api/accounts/1/imports
     *
     * <p>Последние прогоны, новые сверху. Всегда массив, даже если прогонов ещё не было,
     * — чтобы клиенту не приходилось различать пустой список и {@code null}.
     *
     * <p>Запрос не фильтрует по виду импорта: клиенту полезно видеть и финансовые, и
     * импорты каталога в одной истории.
     */
    @GetMapping
    public List<ImportProgress> listImports(@PathVariable Long accountId) {
        SellerAccount account = accountLookup.requireAccount(accountId);
        return importService.recentFor(account.getId());
    }

    /**
     * GET /api/accounts/1/imports/{importId}
     *
     * <p>Прогресс одного прогона. Опросяте этот метод, пока {@code inProgress} не станет
     * {@code false}, чтобы показать полосу прогресса.
     *
     * <p>Чужой прогон выглядит как отсутствующий, а не как запрещённый: иначе перебором
     * номеров можно было бы узнать, сколько у других магазинов загрузок.
     *
     * @return 200 с прогрессом либо 404, если прогона с таким номером нет
     */
    @GetMapping("/{importId}")
    public ResponseEntity<ImportProgress> getImport(@PathVariable Long accountId,
                                                    @PathVariable Long importId) {
        SellerAccount account = accountLookup.requireAccount(accountId);
        return importService.progressOf(importId, account.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}