# Архитектура папок

Как устроен код и почему он разложен именно так.

---

## Главное правило

**Корень пакета — бизнес-возможность, а не техническая роль.**

Раньше код был разложен по ролям: `api` (контроллеры), `persistence` (сущности и
репозитории), `catalog`, `sync`, `analytics`, `marketplace`. Одна фича при этом
расползалась по трём пакетам, и чтобы понять, что происходит с каталогом товаров,
надо было знать, что его контроллер лежит в `api`, сервис в `catalog`, а сущности в
`persistence/entity`.

Сейчас всё, что относится к одной возможности, лежит рядом. Чтобы понять, что
происходит с фичей, открываешь один каталог.

| Пакет | Что делает |
|---|---|
| [`auth`](#auth) | Регистрация, вход, сессия |
| [`account`](#account) | Кто пользователь и какие у него магазины |
| [`sync`](#sync) | Загрузка данных с маркетплейса |
| [`catalog`](#catalog) | Каталог товаров и авторы |
| [`analytics`](#analytics) | Отчёты по нашим данным |
| [`integration`](#integration) | Общение с внешним API маркетплейсов |
| [`web`](#web) | Общий HTTP-слой: формат ошибок |

---

## Слои внутри фичи

Внутри каждой фичи классы разложены по слоям. Набор слоёв может отличаться: пустые
папки не создаются.

| Слой | Что лежит |
|---|---|
| `api` | Контроллеры. Только HTTP: разбор запроса и вызов сервиса |
| `application` | Сервисы: сценарии, которые выполняются по запросу или по фоновой задаче |
| `domain` | Правила и данные: сущности, значения, отчёты. Здесь же `FinancialModel` — арифметика |
| `repository` | Репозитории JPA |
| `infrastructure` | Всё, что трогает внешний мир: SQL напрямую, HTTP-клиенты, схема, наполняемая загрузкой |
| `config` | Конфигурация бинов фичи |
| `model` | Общее представление, переводимое из ответа внешнего мира |

### Почему `domain`, а не `entity`

`domain` — не только сущности. Там лежат и `PeriodCoverage`, и `FinancialModel`, и
все записи отчётов: то, что имеет смысл без базы и без Spring. По имени папки видно,
где искать правило и где искать аннотации.

### Почему `infrastructure`, а не `persistence`

Отдельного пакета `persistence` больше нет. Схема таблиц разложена по фичам, которым
она принадлежит: финансовые таблицы наполняет только загрузка, значит они и лежат
в `sync/infrastructure`. Отчёты читают их через SQL и сущностей не касаются.

Единая папка со всей схемой выглядела бы приятно на бумаге, но стоила бы того, что
сущность начислений лежит в одной фиче, а её репозиторий читает другая: полезно знать
владельца каждой таблицы, а он не меняется.

---

## auth — вход и выход

```
auth/
├── api/        AuthController
├── config/     SecurityConfig
├── domain/     AppUser, AuthService, AppUserDetailsService
└── repository/ AppUserRepository
```

**Почему `AppUser` здесь, а не в `account`.** Логин и хэш пароля — это идентичность, и
она нужна ровно для входа. Продавец — другое дело: он может быть не один, у него есть
клиентский идентификатор и ключ от маркетплейса.

**Почему `SecurityConfig` в `auth/config`, а не в общем `config`.** Отдельного пакета
`config` в проекте нет: выносить настройку одной фичи в общий пакет — значит
добавить indirection без выгоды. Когда появится вторая настройка, не связанная с
фичей, тогда и понадобится.

## account — кто пользователь и какие у него магазины

```
account/
├── api/        MarketplaceController, MarketplaceConnectionController
├── domain/     AccountLookup, CurrentUser, Marketplace, MarketplaceCredentials,
│               MarketplaceProvisioner, SellerAccount
└── repository/ MarketplaceRepository, SellerAccountRepository
```

Это ядро, к которому тянутся остальные фичи: каждой нужен аккаунт продавца, чтобы
выбрать его данные. Поэтому здесь живут `SellerAccount`, справочник `Marketplace` и
реквизиты.

`AccountLookup` — единственное место, где живёт доступ к аккаунту текущего
пользователя. Раньше он лежал в `persistence` и оттуда импортировал адаптеры
маркетплейсов: получалось, что слой хранения знает про внешний мир. Теперь порт
`MarketplaceProvisioner` объявлен здесь же, и `account` ни о ком не знает.

`CurrentUser` читает сессию. **В фоновых потоках он не работает** — там сессии нет,
поэтому задачи импорта получают `accountId` параметром.

## sync — загрузка данных с маркетплейса

```
sync/
├── api/                 CoverageController, ImportController
├── application/        AccrualImportService, AccrualWriter, DayStateService,
│                       ImportProgressListener, ImportProperties, ImportService
├── domain/             AccrualImportReport, AccrualType, DayStatus,
│                       ImportConflictException, ImportedDay, ImportProgress,
│                       ImportRun, ImportType, PeriodCoverage, RunState
├── infrastructure/
│   ├── entity/         ContainerFee, DeliveryService, FinanceAccrual, ItemFee,
│   │                   ItemFeeDetail, NonItemFee, Posting, PostingProduct
│   └── repository/     репозитории этих восьми сущностей
└── repository/         AccrualTypeRepository, ImportedDayRepository,
                        ImportRunRepository
```

**Почему фича называется `sync`, а не `import`.** `import` — зарезервированное слово
Java, пакет `ru.analizer.import` не компилируется. Имя `sync` уже используется в
префиксе конфигурации `sync.*` и в `thread-name-prefix`, так что код и настройки
говорят об одном.

Разделение `repository` и `infrastructure/repository` намеренное: первые три
репозитория работают с сущностями самой загрузки, вторые шесть — с финансовой
схемой, которую загрузка наполняет.

`sync -> catalog` и `sync -> account` — зависимости по делу: `ImportService`
запускает загрузку каталога и всегда работает от аккаунта.

## catalog — каталог товаров и авторы

```
catalog/
├── api/         CatalogController
├── application/ CatalogImportProgressListener, CatalogImportService, ProductWriter
├── domain/      AuthorExtractor, CatalogImportReport,
│                OzonProduct, OzonProductAttribute, ProductAuthor
└── repository/  OzonProductAttributeRepository, OzonProductRepository,
                 ProductAuthorRepository
```

Имена авторов хранятся ровно так, как их написал продавец, — отдельной строкой на
автора в `product_author`. Разбор приводит только к одному: запятая внутри значения
делит список авторов, пробелы по краям убираются. Ни приведения регистра, ни сведения
к инициалам здесь нет сознательно — склейка разных написаний одного человека угадывала
бы, а в отчёте о продажах лишний товар обходится дороже неудобного фильтра.

## analytics — отчёты по нашим данным

```
analytics/
├── api/             AnalyticsController
├── application/     DailyAnalyticsService, ProductAnalyticsService
├── domain/          DailyReport, DailyRow, FeeFact, FinancialModel,
│                    FinancialSummary, ProductFact, ProductReport,
│                    ReportCoverage, ReportStatus
└── infrastructure/  AnalyticsFactsRepository, CatalogFacts
│   └── filter/      ProductAttributeFilter, ProductAuthorFilter
```

Отбор товаров по признакам вынесен в `filter`: фильтр отдаёт кусок SQL-условия и свои
параметры, а запрос складывает условия сам. Сейчас реализация одна — по автору, но
описание устроено так, чтобы добавление признака не требовало правок в выборках.

Отчёты читают базу через `infrastructure`, а не через JPA-сущности. Для этого есть
причина: отчёт считает за месяц по нескольким таблицам, и выразить это на JPQL
проще одной выборкой, чем десятком запросов и склейкой в памяти.

`FinancialModel` — арифметика, проверяемая тождеством «доходы − расходы = к выплате».
Она живёт в `domain` и не знает ни про SQL, ни про Spring.

**Зависимость `analytics -> sync` намеренная.** Отчёт обязан знать, какой период
загружен, иначе нули выглядели бы как «денег не было». Отказываться от неё нельзя,
поэтому она сделана явной: `PeriodCoverage` приходит из `sync/domain`.

## integration — общение с внешним миром

```
integration/
├── MarketplaceAdapter, ProductCatalogAdapter, CatalogPager,
│   CredentialsRejectedException
├── model/          AccrualDto, AccrualPage, AccrualTypeInfo, CatalogPage,
│                   ProductAttributeEntry, ProductEntry
└── ozon/
    ├── OzonAdapter, OzonCatalogAdapter, OzonClient, OzonMapper, … (9 файлов)
    ├── dto/catalog/   ответ /v4/product/info/attributes
    ├── dto/finance/   ответы финансовых методов
    └── json/          десериализаторы, которыми OZON отвечает неожиданно
```

Единственное место, где приложение знает про OZON. Добавить Wildberries — значит
добавить `integration/wildberries` и ничего больше.

`model` — то, во что переводится ответ маркетплейса: дальше приложение работает с
этим, а не с `Posting` и `ItemFee` ответа OZON. Такое представление ничего не знает о
нашей схеме.

`dto/finance` и `dto/catalog` разведены, потому что это ответы **разных методов
API**. Раньше все 24 DTO лежали в одной папке, и принадлежность к методу приходилось
выводить по имени файла.

## web — общий HTTP-слой

```
web/  ApiExceptionHandler, NotConnectedException, UnknownMarketplaceException
```

Здесь только то, что общее для всех фич: единый формат ошибок RFC 7807 и два
исключения, которые мы показываем клиенту. Отдельного `api` больше нет — каждый
контроллер живёт у своей фичи, иначе их пришлось бы искать в другом месте, чем
остальной код этой фичи.

---

## Как читать прикладную задачу

Пример: «почему в отчёте за месяц нули?»

1. `analytics/api/AnalyticsController` — как приходит запрос;
2. `analytics/application/ProductAnalyticsService` — как считается;
3. `analytics/infrastructure` — как читается база;
4. `sync/domain/PeriodCoverage` — не загружен ли период, и `sync/api/CoverageController`
   — как это узнать.

Четыре пакета, ни одного «где-то в persistence».

---

## Тесты повторяют структуру приложения

Правило: **тест лежит в пакете того main-класса, который он проверяет.** Поэтому место
теста отвечает на вопрос «что он сломает, если покраснеет».

```
test/java/ru/analizer/
├── support/                 базовые классы, заглушки адаптеров, их конфигурации
├── contract/                сквозные проверки API, не принадлежащие одной фиче
├── account/api/             подключение реквизитов
├── auth/api/                вход
├── sync/
│   ├── application/         DayStateService
│   └── integration/         загрузка на настоящем PostgreSQL
├── catalog/
│   ├── domain/              авторы
│   └── integration/         каталог
├── analytics/
│   ├── domain/              арифметика
│   └── integration/         отчёты
└── integration/
    ├── model/               постраничный обход
    └── ozon/                разбор ответов, транспорт, пагинация
```

`support` ничего не проверяет сама, поэтому отделена от проверок: обвязка и
проверяемое поведение в одной папке — источник путаницы.

| Окончание | Что это | Проверяет ли сам |
|---|---|---|
| `Test` | Модульный тест, без Spring | да |
| `IT` | Интеграционный, на PostgreSQL в контейнере | да |
| `*Config`, `*FixtureAdapter`, `FixtureAdapters` | Обвязка | нет |

---

## Проверить, что не поехало

```powershell
.\gradlew.bat compileJava          # компиляция
.\gradlew.bat test -Pfast          # модульные тесты, секунды
.\gradlew.bat test                 # всё, около 7 минут
.\scripts\check-api.ps1           # работающее приложение и честность цифр
```

Полный прогон медленный не из-за самих проверок: почти всё время уходит на старт
Spring-контекста и Testcontainers. `-Pfast` их не поднимает.