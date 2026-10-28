import type { components } from '@/api/schema'
import type * as T from '@/types'

/**
 * Сверка типов интерфейса с машинным контрактом.
 *
 * <p>Контракт лежит в `docs/openapi.json` и пересобирается из кода, а типы
 * интерфейса написаны руками. Со временем они расходятся: сервер добавляет
 * поле, а интерфейс о нём не знает — и читает его как «неизвестное». Или наоборот:
 * в типах остаётся поле, которого сервер уже не отдаёт, и оно молча равно
 * `undefined`.
 *
 * <p>Проверка ниже ловит оба случая на этапе сборки. Она ничего не стоит в
 * рантайме: это объявления типов, которые TypeScript проверяет при компиляции.
 * Расхождение видно в сообщении об ошибке: там названы пропавшие или лишние
 * поля.
 *
 * <p>Пересобрать типы из контракта: `npm run gen:types`.
 */

type Contract = components['schemas']

/** Поля контракта, которых нет в типе интерфейса. */
type Lost<TApp, TContract> = Exclude<keyof TContract, keyof TApp>

/** Поля типа интерфейса, которых нет в контракте. */
type Invented<TApp, TContract> = Exclude<keyof TApp, keyof TContract>

/**
 * Результат сверки: `true`, если типы совпадают по составу полей.
 *
 * <p>При расхождении тип становится объектом с именем пропавших или лишних
 * полей, и присвоить ему `true` уже нельзя.
 */
type Check<TApp, TContract> = [Lost<TApp, TContract>] extends [never]
  ? [Invented<TApp, TContract>] extends [never]
    ? true
    : { лишние_поля: Invented<TApp, TContract> }
  : { потерянные_поля: Lost<TApp, TContract> }

// ─── отчёты ──────────────────────────────────────────────────────────────────

export const _dailyReport: Check<T.DailyReport, Contract['DailyReport']> = true
export const _dailyRow: Check<T.ApiDay, Contract['DailyRow']> = true
export const _expenseBreakdown: Check<T.ExpenseBreakdown, Contract['ExpenseBreakdown']> = true
export const _expenseGroup: Check<T.ExpenseGroup, Contract['ExpenseGroup']> = true
export const _expenseItem: Check<T.ExpenseItem, Contract['TypeAmount']> = true
export const _breakdown: Check<T.DailyBreakdown, Contract['Breakdown']> = true
export const _financialSummary: Check<T.DailyReport['total'], Contract['FinancialSummary']> = true
export const _productReport: Check<T.ProductsReport, Contract['ProductReport']> = true
export const _productRow: Check<T.ProductRow, Contract['ProductRow']> = true
export const _productTotals: Check<T.ProductsTotals, Contract['ProductTotals']> = true
export const _selectionTotals: Check<T.SelectionTotals, Contract['SelectionTotals']> = true
export const _coverage: Check<T.Coverage, Contract['ReportCoverage']> = true
export const _salesDynamics: Check<T.SalesDynamics, Contract['SalesDynamicsReport']> = true
export const _productAuthor: Check<T.ProductAuthor, Contract['ProductAuthorView']> = true

// ─── прочее ──────────────────────────────────────────────────────────────────

export const _accountInfo: Check<T.AccountInfo, Contract['AccountInfo']> = true
export const _marketplaceInfo: Check<T.MarketplaceInfo, Contract['MarketplaceInfo']> = true
export const _importProgress: Check<T.ImportProgress, Contract['ImportProgress']> = true
export const _catalogState: Check<T.CatalogState, Contract['CatalogState']> = true
