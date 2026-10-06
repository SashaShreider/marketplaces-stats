// ─────────────────────────────────────────────────────────────────────────────
//  Типизированные методы REST API. Структура повторяет документацию:
//   /api/auth/*                          — вход и сессия
//   /api/marketplaces                    — справочник и подключения
//   /api/marketplaces/{mp}/imports/*     — команды (тратят квоту маркетплейса!)
//   /api/marketplaces/{mp}/data/*        — состояние нашей базы (бесплатно)
//   /api/marketplaces/{mp}/analytics/*   — отчёты (бесплатно)
// ─────────────────────────────────────────────────────────────────────────────

import { forgetCsrf, refreshCsrf, request } from './http'
import type {
  CatalogState,
  Coverage,
  DailyReport,
  ImportProgress,
  MarketplaceInfo,
  ProductSort,
  ProductsReport,
  User,
} from '../types'

const mpPath = (code: string) => `/api/marketplaces/${encodeURIComponent(code.toLowerCase())}`

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

// ─── Маркетплейсы ────────────────────────────────────────────────────────────

export const listMarketplaces = () => request<MarketplaceInfo[]>('GET', '/api/marketplaces')

export const putCredentials = (code: string, clientId: string, apiKey: string) =>
  request<{ marketplace: string; clientId: string }>('PUT', `${mpPath(code)}/credentials`, {
    body: { clientId, apiKey },
  })

export const deleteMarketplace = (code: string) => request<void>('DELETE', mpPath(code))

// ─── Импорты (расходуют квоту маркетплейса) ──────────────────────────────────

export const startFinanceImport = (code: string, dateFrom: string, dateTo: string) =>
  request<ImportProgress>('POST', `${mpPath(code)}/imports/finance`, {
    query: { dateFrom, dateTo },
  })

export const startCatalogImport = (code: string) =>
  request<ImportProgress>('POST', `${mpPath(code)}/imports/catalog`)

export const listImports = (code: string) =>
  request<ImportProgress[]>('GET', `${mpPath(code)}/imports`)

export const getImport = (code: string, id: number) =>
  request<ImportProgress>('GET', `${mpPath(code)}/imports/${id}`)

// ─── Состояние данных ────────────────────────────────────────────────────────

export const getCoverage = (code: string, dateFrom: string, dateTo: string) =>
  request<Coverage>('GET', `${mpPath(code)}/data/coverage`, { query: { dateFrom, dateTo } })

export const getCatalogState = (code: string) =>
  request<CatalogState>('GET', `${mpPath(code)}/data/catalog`)

export const getAuthors = (code: string) => request<string[]>('GET', `${mpPath(code)}/data/authors`)

// ─── Аналитика ───────────────────────────────────────────────────────────────

export const getDaily = (code: string, dateFrom: string, dateTo: string) =>
  request<DailyReport>('GET', `${mpPath(code)}/analytics/daily`, { query: { dateFrom, dateTo } })

export interface ProductsQuery {
  dateFrom: string
  dateTo: string
  author?: string
  query?: string
  sort?: ProductSort
  page?: number
  size?: number
}

export const getProducts = (code: string, q: ProductsQuery) =>
  request<ProductsReport>('GET', `${mpPath(code)}/analytics/products`, {
    query: { ...q },
  })
