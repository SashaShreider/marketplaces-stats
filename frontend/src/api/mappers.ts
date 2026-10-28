import type { DailyReport, DayFinance, DayState } from '@/types'

/** Преобразует ответ analytics/daily в модель интерфейса (расходы → положительные числа) */
export function mapDaily(report: DailyReport): DayFinance[] {
  const missing = new Set(report.coverage.missingDays)
  const failed = new Set(report.coverage.failedDates)
  const provisional = new Set(report.coverage.provisionalDays)

  return report.days.map((d) => {
    const b = d.breakdown
    const state: DayState = failed.has(d.date)
      ? 'failed'
      : missing.has(d.date)
        ? 'missing'
        : provisional.has(d.date)
          ? 'provisional'
          : 'ok'

    return {
      date: d.date,
      state,
      sales: b.sales,
      returns: Math.abs(b.returns),
      partners: b.partnerProgramme,
      income: d.income,
      commission: -b.commission,
      logistics: -b.logistics,
      other: -b.otherExpenses,
      expenses: d.expenses,
      // Прибыль считается из доходов и расходов, а не из выплаты: при отборе по
      // товарам выплата неизвестна, и вычитать её означало бы вычесть ноль.
      profit: d.income - d.expenses,
      soldQty: d.soldQuantity ?? 0,
      returnedQty: d.returnedQuantity ?? 0,
      payout: d.payout,
      expenseBreakdown: d.expenseBreakdown ?? { groups: [], total: 0 },
    }
  })
}
