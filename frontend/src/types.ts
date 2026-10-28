// ─── DTO бэкенда (повторяют docs/API) ───────────────────────────────────────

export interface User {
  login: string
  displayName: string
}

/**
 * Элемент GET /api/marketplaces — справочник платформ.
 *
 * Отвечает на вопрос «куда вообще можно подключиться». Подключённые магазины
 * перечисляет отдельный метод: GET /api/accounts.
 */
export interface MarketplaceInfo {
  /** Код платформы, например "OZON". В тело POST /api/accounts */
  code: string
  /** Отображаемое название платформы */
  name: string
  /** Есть ли у пользователя хотя бы один магазин на этой платформе */
  connected: boolean
}

/**
 * Элемент GET /api/accounts — подключённый магазин.
 *
 * <p>Магазин, а не платформа: у продавца бывает несколько кабинетов на одной
 * площадке, и у каждого свои начисления и каталог. Поэтому в пути подставляется
 * `id`, а не код платформы: код перестал быть однозначным выбором.
 */
export interface AccountInfo {
  /** Подставляется в путь всех прочих запросов */
  id: number
  /** Код платформы, на которой работает магазин */
  marketplaceCode: string
  /** Название платформы для интерфейса */
  marketplaceName: string
  /**
   * Название магазина от маркетплейса. У двух магазинов одной платформы может
   * совпадать, поэтому в списке рядом показывают clientId.
   */
  shopName: string
  clientId: string
  /** Секретного ключа здесь не бывает — сервер его не отдаёт */
  catalogProducts: number
  importRuns: number
}

export type ImportStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED' | 'CANCELLED'

export interface ImportProgress {
  id: number
  marketplace: string
  importType: 'FINANCE' | 'CATALOG'
  dateFrom: string | null
  dateTo: string | null
  status: ImportStatus
  totalUnits: number
  doneUnits: number
  skippedUnits: number
  failedUnits: number
  currentUnit: string | null
  error: string | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
  inProgress: boolean
  percent: number
}

export type ReportStatus = 'READY' | 'PARTIAL' | 'NOT_LOADED' | 'HAS_ERRORS'

export interface Coverage {
  requestedDays: number
  loadedDays: number
  finalDays: number
  failedDays: number
  percentLoaded: number
  missingDays: string[]
  provisionalDays: string[]
  failedDates: string[]
  fullyFinal: boolean
  needsSync: boolean
}

export interface DailyBreakdown {
  sales: number
  returns: number
  partnerProgramme: number
  commission: number
  logistics: number
  otherExpenses: number
}

export interface ApiDay {
  date: string
  income: number
  expenses: number
  payout: number
  breakdown: DailyBreakdown
  soldQuantity?: number | null
  returnedQuantity?: number | null
  expensesByType: {
      typeId: number
      /** служебное название типа начисления, например PayPerClick */
      name: string
      /** человеческое описание из справочника маркетплейса, например «Оплата за клик» */
      description?: string | null
      amount: number
    }[]
}

export interface DailyReport {
  marketplace: string
  dateFrom: string
  dateTo: string
  status: ReportStatus
  coverage: Coverage
  days: ApiDay[]
  income: number
  expenses: number
  payout: number
  /**
   * Итог за период: раскладка по статьям плюс количество за период.
   *
   * <p>Количество есть только здесь — на верхнем уровне отчёта его нет, в отличие
   * от денег. Это известная непоследовательность формы ответа, помечена
   * `TODO(#response-duplication)` в коде сервера.
   */
  total: DailyBreakdown & {
    dateFrom: string
    dateTo: string
    payout: number
    reconciles: boolean
    soldQuantity: number
    returnedQuantity: number
  }
  reconciled: boolean
  final: boolean
}

export interface CatalogState {
  products: number
  lastSyncedAt: string | null
  loaded: boolean
}

export interface ProductAuthor {
  raw: string
  source: 'DECLARED' | 'COVER'
  primary: boolean
}

export interface ProductRow {
  sku: number
  offerId: string | null
  name: string
  primaryImage: string | null
  isbn: string | null
  typeId: number | null
  authors: ProductAuthor[]
  soldQuantity: number
  returnedQuantity: number
  accrualCount: number
  financial: DailyBreakdown & { payout: number; reconciles: boolean }
  income: number
  expenses: number
  /** Товар снят с продажи и убран в архив OZON: в кабинете его уже нет, но начисления остались. */
  archived: boolean
}

/** Один день динамики продаж: количество единиц и деньги этого дня. */
export interface SalesDay {
  date: string
  sold: number
  returned: number
  /**
   * Доходы: цена продаж за вычетом возвратов плюс бонусы и ковейст.
   *
   * <p>Денежные поля приходят со знаком, как их отдаёт OZON: доход положителен,
   * расходы отрицательны. Так же устроены остальные отчёты.
   *
   * <p>В расходы входят только начисления с привязкой к SKU: это экономика книги,
   * а не расходы магазина. Поэтому выручка дня сходится с выручкой товара в таблице
   * на этой же странице.
   */
  income: number
  /** Комиссия маркетплейса */
  commission: number
  /** Логистика */
  logistics: number
  /** Продвижение по товару: Promotion + PayPerClick + StarsMembership, если у них есть SKU */
  promotion: number
  /** Прочие расходы по товару, уже без продвижения */
  otherExpenses: number
  /** Выручка: доходы минус расходы книги, включая продвижение */
  revenue: number
}

/**
 * Динамика продаж по дням периода.
 *
 * Дни без операций в days не попадают — пропуски восстанавливаются по дате, а
 * состояние незагруженных дней приходит в coverage. Без него «день без продаж» и
 * «день не загружен» на графике выглядели бы одинаково.
 */
export interface SalesDynamics {
  dateFrom: string
  dateTo: string
  status: ReportStatus
  coverage: Coverage
  days: SalesDay[]
  /** Чем ограничен отчёт: «Все товары», имя автора или «SKU …». */
  filter: string
  /**
   * Расходы периода без привязки к SKU, по модулю.
   *
   * <p>В дни и в выручку они не входят — это расходы магазина, а не книги, — но
   * молчать о них нельзя: иначе расходы динамики не сойдутся с дневным отчётом.
   */
  unallocatedExpenses: number
}

export interface ProductsTotals {
  income: number
  expenses: number
  payout: number
  sales: number
  returns: number
  partnerProgramme: number
  commission: number
  logistics: number
  itemExpenses: number
  soldQuantity?: number
  returnedQuantity?: number
  productsInCatalog: number
  productsWithSales: number
}

/**
 * Итоги по текущему отбору — их показывает строка «Итого» под таблицей.
 *
 * Отличаются от ProductsTotals тем, что считаются по всем товарам, попавшим
 * под фильтр, а не по всему каталогу за период. Карточки наверху читают
 * ProductsTotals, эта строка — SelectionTotals: один вопрос — одно число.
 */
export interface SelectionTotals {
  income: number
  expenses: number
  soldQuantity: number
}

/**
 * Критерии сортировки таблицы товаров.
 *
 * <p>По одному на колонку: так заголовок таблицы и есть выбор критерия, а список
 * критериев в отдельном месте не нужен.
 */
export type ProductSort = 'INCOME' | 'NAME' | 'SKU' | 'SOLD' | 'EXPENSES' | 'GROSS'

/** Направление сортировки: по возрастанию или по убыванию. */
export type ProductSortOrder = 'asc' | 'desc'

export interface ProductsReport {
  marketplace: string
  dateFrom: string
  dateTo: string
  status: ReportStatus
  coverage: Coverage
  catalog: CatalogState
  totals: ProductsTotals
  selectionTotals: SelectionTotals
  rows: ProductRow[]
  page: number
  size: number
  totalRows: number
  totalPages: number
  unallocatedExpenses: number
  skusMissingFromCatalog: number[]
}

// ─── Модель интерфейса ──────────────────────────────────────────────────────

export type DayState = 'ok' | 'missing' | 'failed' | 'provisional'

/** Показатели одного дня в удобном для UI виде: расходы — положительные числа */
export interface DayFinance {
  date: string
  state: DayState
  sales: number
  /** модуль возвратов */
  returns: number
  partners: number
  income: number
  commission: number
  logistics: number
  other: number
  /** расшифровка расходов по типам начислений (положительные числа) */
  otherByType: { name: string; description?: string | null; amount: number }[]
  expenses: number
  profit: number
  soldQty: number
  returnedQty: number
}

export type FinanceTotals = Omit<DayFinance, 'date' | 'state'>

export interface DateRange {
  from: string
  to: string
}
