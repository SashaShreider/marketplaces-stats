// ─────────────────────────────────────────────────────────────────────────────
//  Типизированные методы REST API. Структура повторяет документацию:
//   /api/auth/*                          — вход и сессия
//   /api/marketplaces                    — справочник платформ (куда подключаться)
//   /api/accounts                        — мои магазины
//   /api/accounts/{id}/imports/*         — команды (тратят квоту маркетплейса!)
//   /api/accounts/{id}/data/*            — состояние нашей базы (бесплатно)
//   /api/accounts/{id}/analytics/*       — отчёты (бесплатно)
//
//  В путях подставляется идентификатор магазина, а не код платформы. Раньше
//  магазин выбирался кодом маркетплейса, и это работало, пока он был один на
//  платформу; с несколькими кабинетами выбор стал неоднозначным.
// ─────────────────────────────────────────────────────────────────────────────

import { forgetCsrf, refreshCsrf, request } from '@/api/http'
import type {
  AccountInfo,
  DailyReport,
  ImportProgress,
  MarketplaceInfo,
  ProductOption,
  ProductSort,
  ProductSortOrder,
    ProductsReport,
    SalesDynamics,
    User,
  } from '@/types'

const accountPath = (accountId: number) => `/api/accounts/${accountId}`

// ─── Аутентификация ──────────────────────────────────────────────────────────

export const me = () => request<User>('GET', '/api/auth/me', { silent401: true })

export async function login(loginName: string, password: string): Promise<User> {
  const user = await request<User>('POST', '/api/auth/login', {
    body: { login: loginName, password },
    silent401: true,
  })
  // после входа сессия новая — токен CSRF мог смениться
  await refreshCsrf()
  return user
}

export const register = (loginName: string, password: string, displayName: string) =>
  request<User>('POST', '/api/auth/register', {
    body: { login: loginName, password, displayName: displayName || undefined },
    silent401: true,
  })

export async function logout(): Promise<void> {
  try {
    await request<void>('POST', '/api/auth/logout', { silent401: true })
  } finally {
    forgetCsrf()
  }
}

// ─── Платформы: справочник для окна подключения ──────────────────────────────

export const listMarketplaces = () => request<MarketplaceInfo[]>('GET', '/api/marketplaces')

// ─── Мои магазины ─────────────────────────────────────────────────────────────

export const listAccounts = () => request<AccountInfo[]>('GET', '/api/accounts')

/**
 * Подключает новый магазин. Отдельное действие от замены ключей: подключение
 * заводит магазин, а замена ключей чинит уже существующий.
 *
 * `marketplace` — код платформы из GET /api/marketplaces. В остальных запросах он
 * не встречается.
 */
export const createAccount = (marketplace: string, clientId: string, apiKey: string) =>
  request<AccountInfo>('POST', '/api/accounts', {
    body: { marketplace, clientId, apiKey },
  })

export const putAccountCredentials = (accountId: number, clientId: string, apiKey: string) =>
  request<AccountInfo>('PUT', `${accountPath(accountId)}/credentials`, {
    body: { clientId, apiKey },
  })

export const deleteAccount = (accountId: number) =>
  request<void>('DELETE', accountPath(accountId))

// ─── Импорты (расходуют квоту маркетплейса) ──────────────────────────────────

export const startFinanceImport = (accountId: number, dateFrom: string, dateTo: string) =>
  request<ImportProgress>('POST', `${accountPath(accountId)}/imports/finance`, {
    query: { dateFrom, dateTo },
  })

export const startCatalogImport = (accountId: number) =>
  request<ImportProgress>('POST', `${accountPath(accountId)}/imports/catalog`)

export const listImports = (accountId: number) =>
  request<ImportProgress[]>('GET', `${accountPath(accountId)}/imports`)

export const getImport = (accountId: number, id: number) =>
  request<ImportProgress>('GET', `${accountPath(accountId)}/imports/${id}`)

// ─── Состояние данных ────────────────────────────────────────────────────────

export const getAuthors = (accountId: number) =>
  request<string[]>('GET', `${accountPath(accountId)}/data/authors`)

/**
 * Товары для подсказки в фильтре — короткие строки, без периода и денег.
 *
 * <p>Отдельно от {@link getProducts}: тот считает финансовый отчёт по каждому
 * товару за период, и дёргать его на каждый введённый символ было бы расточительно.
 */
export const getProductOptions = (accountId: number, query: string, limit = 20) =>
  request<ProductOption[]>('GET', `${accountPath(accountId)}/data/products`, {
    query: { query, limit },
  })

// ─── Аналитика ───────────────────────────────────────────────────────────────

/**
 * Дневной отчёт с отбором.
 *
 * <p>`skus` и `author` — два разных сужения, и передавать оба смысла нет: сервер
 * при заданных `skus` игнорирует `author`. На странице они и не выставляются
 * одновременно: выбрав товар, человек уже ответил, чей он.
 */
export const getDaily = (
  accountId: number,
  dateFrom: string,
  dateTo: string,
  scope: { author?: string; skus?: number[] } = {},
) =>
  request<DailyReport>('GET', `${accountPath(accountId)}/analytics/daily`, {
    query: { dateFrom, dateTo, author: scope.author, skus: scope.skus },
  })

interface ProductsQuery {
  dateFrom: string
  dateTo: string
  author?: string
  query?: string
  /** Критерий сортировки: по одному на колонку таблицы */
  sort?: ProductSort
  /** Направление сортировки */
  order?: ProductSortOrder
  /** Оставить только товары, у которых были продажи */
  onlyWithSales?: boolean
  page?: number
  size?: number
}

export const getProducts = (accountId: number, q: ProductsQuery) =>
  request<ProductsReport>('GET', `${accountPath(accountId)}/analytics/products`, {
    query: { ...q },
  })

/**
 * Динамика продаж по дням.
 *
 * author и sku несовместимы: это два разных отбора, и бэкенд при заданном sku
 * игнорирует автора. Передача обоих сразу означало бы искать товар сразу у двух
 * продавцов, а задача у пользователя одна.
 */
export const getSalesDynamics = (
  accountId: number,
  q: { dateFrom: string; dateTo: string; author?: string; sku?: number },
) =>
  request<SalesDynamics>('GET', `${accountPath(accountId)}/analytics/products/dynamics`, {
    query: { ...q },
  })