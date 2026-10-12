// ─── Предпросмотр компонента из другого проекта ─────────────────────────────
// Файл перенесён как есть из D:\Users\sasha\Desktop\components\FinancialDynamics.
// Изменены только импорты под этот проект: тип и форматтеры. Всё остальное —
// разметка, цвета и поведение — оригинальные, иначе сравнивать было бы не с чем.
// Правки в оформлении вносить сюда не стоит: на правках в вашем проекте
// смотреть нечего.

import React, { useState, useMemo } from 'react';
import { ChevronLeft, ChevronRight, ArrowUpDown, Download, Search } from 'lucide-react';
import { DayFinancialData } from './types';
import { fmtMoney as formatCurrency } from '@/utils/format';

interface FinanceTableProps {
  data: DayFinancialData[];
}

type SortField =
  | 'date'
  | 'sales'
  | 'returns'
  | 'partnerPrograms'
  | 'income'
  | 'commission'
  | 'logistics'
  | 'otherExpenses'
  | 'totalExpenses'
  | 'profit'
  | 'marginPercent';

export const FinanceTable: React.FC<FinanceTableProps> = ({ data }) => {
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize] = useState(15);
  const [searchQuery, setSearchQuery] = useState('');
  const [sortField, setSortField] = useState<SortField>('date');
  const [sortAsc, setSortAsc] = useState(false);

  // Filtered by search query
  const filteredData = useMemo(() => {
    if (!searchQuery.trim()) return data;
    const query = searchQuery.toLowerCase();
    return data.filter(
      (d) =>
        d.displayDate.toLowerCase().includes(query) ||
        d.fullDateStr.toLowerCase().includes(query) ||
        d.date.includes(query)
    );
  }, [data, searchQuery]);

  // Sorted data
  const sortedData = useMemo(() => {
    return [...filteredData].sort((a, b) => {
      let valA: any = a[sortField];
      let valB: any = b[sortField];

      if (typeof valA === 'string') {
        return sortAsc ? valA.localeCompare(valB) : valB.localeCompare(valA);
      }
      return sortAsc ? valA - valB : valB - valA;
    });
  }, [filteredData, sortField, sortAsc]);

  // Pagination calculation
  const totalPages = Math.max(1, Math.ceil(sortedData.length / pageSize));
  const pageIndex = Math.min(currentPage, totalPages);
  const startIndex = (pageIndex - 1) * pageSize;
  const pageRows = sortedData.slice(startIndex, startIndex + pageSize);

  // Overall Totals across all loaded days
  const totals = useMemo(() => {
    return data.reduce(
      (acc, item) => {
        acc.sales += item.sales;
        acc.returns += item.returns;
        acc.partnerPrograms += item.partnerPrograms;
        acc.income += item.income;
        acc.commission += item.commission;
        acc.logistics += item.logistics;
        acc.otherExpenses += item.otherExpenses;
        acc.totalExpenses += item.totalExpenses;
        acc.profit += item.profit;
        return acc;
      },
      {
        sales: 0,
        returns: 0,
        partnerPrograms: 0,
        income: 0,
        commission: 0,
        logistics: 0,
        otherExpenses: 0,
        totalExpenses: 0,
        profit: 0,
      }
    );
  }, [data]);

  const totalMarginPercent = totals.income > 0 ? Math.round((totals.profit / totals.income) * 100) : 0;

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortAsc(!sortAsc);
    } else {
      setSortField(field);
      setSortAsc(false);
    }
  };

  // Export to CSV
  const handleExportCsv = () => {
    const headers = [
      'Дата',
      'Продажи',
      'Возвраты',
      'Партнеры',
      'Доходы (Всего)',
      'Комиссия',
      'Логистика',
      'Прочие расходы',
      'Расходы (Всего)',
      'Прибыль',
      'Маржа (%)',
    ];

    const rows = data.map((d) => [
      d.fullDateStr,
      d.sales,
      d.returns,
      d.partnerPrograms,
      d.income,
      d.commission,
      d.logistics,
      d.otherExpenses,
      d.totalExpenses,
      d.profit,
      `${d.marginPercent}%`,
    ]);

    const csvContent =
      'data:text/csv;charset=utf-8,\uFEFF' +
      [headers.join(';'), ...rows.map((e) => e.join(';'))].join('\n');

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `analytics_data_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="space-y-3">
      {/* Table toolbar */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-3 text-xs">
        <div className="relative w-full sm:w-64">
          <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Поиск по дате..."
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              setCurrentPage(1);
            }}
            className="w-full pl-8 pr-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-slate-700 placeholder-slate-400 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>

        <div className="flex items-center space-x-2 self-end sm:self-auto">
          <button
            onClick={handleExportCsv}
            className="flex items-center space-x-1.5 px-3 py-1.5 border border-slate-200 rounded-lg hover:bg-slate-50 text-slate-700 transition-colors font-medium shadow-xs"
          >
            <Download className="w-3.5 h-3.5 text-slate-500" />
            <span>Экспорт в CSV</span>
          </button>
        </div>
      </div>

      {/* Main Table Container: scrollable with sticky header and sticky footer */}
      <div className="border border-slate-200/90 rounded-xl overflow-x-auto overflow-y-auto max-h-[460px] bg-white shadow-inner">
        <table className="w-full text-left text-xs whitespace-nowrap border-collapse min-w-[980px]">
          {/* Sticky Table Header */}
          <thead className="sticky top-0 bg-slate-100/95 backdrop-blur-xs text-slate-600 font-semibold z-20 border-b border-slate-200 shadow-xs">
            <tr>
              <th
                onClick={() => handleSort('date')}
                className="py-3 px-3.5 cursor-pointer hover:bg-slate-200/60 transition-colors"
              >
                <div className="flex items-center space-x-1">
                  <span>Дата</span>
                  <ArrowUpDown className="w-3 h-3 text-slate-400" />
                </div>
              </th>
              <th
                onClick={() => handleSort('sales')}
                className="py-3 px-3.5 text-right cursor-pointer hover:bg-slate-200/60 transition-colors"
              >
                <div className="flex items-center justify-end space-x-1">
                  <span>Продажи</span>
                  <ArrowUpDown className="w-3 h-3 text-slate-400" />
                </div>
              </th>
              <th
                onClick={() => handleSort('returns')}
                className="py-3 px-3.5 text-right cursor-pointer hover:bg-slate-200/60 transition-colors"
              >
                <div className="flex items-center justify-end space-x-1">
                  <span>Возвраты</span>
                  <ArrowUpDown className="w-3 h-3 text-slate-400" />
                </div>
              </th>
              <th
                onClick={() => handleSort('partnerPrograms')}
                className="py-3 px-3.5 text-right cursor-pointer hover:bg-slate-200/60 transition-colors"
              >
                <div className="flex items-center justify-end space-x-1">
                  <span>Партнёры</span>
                  <ArrowUpDown className="w-3 h-3 text-slate-400" />
                </div>
              </th>
              <th
                onClick={() => handleSort('income')}
                className="py-3 px-3.5 text-right bg-blue-50/50 cursor-pointer hover:bg-blue-100/50 transition-colors text-blue-900 font-bold"
              >
                <div className="flex items-center justify-end space-x-1">
                  <span>Доходы</span>
                  <ArrowUpDown className="w-3 h-3 text-blue-600" />
                </div>
              </th>
              <th
                onClick={() => handleSort('commission')}
                className="py-3 px-3.5 text-right cursor-pointer hover:bg-slate-200/60 transition-colors"
              >
                <div className="flex items-center justify-end space-x-1">
                  <span>Комиссия</span>
                  <ArrowUpDown className="w-3 h-3 text-slate-400" />
                </div>
              </th>
              <th
                onClick={() => handleSort('logistics')}
                className="py-3 px-3.5 text-right cursor-pointer hover:bg-slate-200/60 transition-colors"
              >
                <div className="flex items-center justify-end space-x-1">
                  <span>Логистика</span>
                  <ArrowUpDown className="w-3 h-3 text-slate-400" />
                </div>
              </th>
              <th
                onClick={() => handleSort('otherExpenses')}
                className="py-3 px-3.5 text-right cursor-pointer hover:bg-slate-200/60 transition-colors"
              >
                <div className="flex items-center justify-end space-x-1">
                  <span>Прочие</span>
                  <ArrowUpDown className="w-3 h-3 text-slate-400" />
                </div>
              </th>
              <th
                onClick={() => handleSort('totalExpenses')}
                className="py-3 px-3.5 text-right bg-rose-50/50 cursor-pointer hover:bg-rose-100/50 transition-colors text-rose-900 font-bold"
              >
                <div className="flex items-center justify-end space-x-1">
                  <span>Расходы</span>
                  <ArrowUpDown className="w-3 h-3 text-rose-600" />
                </div>
              </th>
              <th
                onClick={() => handleSort('profit')}
                className="py-3 px-3.5 text-right bg-emerald-50/50 cursor-pointer hover:bg-emerald-100/50 transition-colors text-emerald-900 font-bold"
              >
                <div className="flex items-center justify-end space-x-1">
                  <span>Прибыль</span>
                  <ArrowUpDown className="w-3 h-3 text-emerald-600" />
                </div>
              </th>
              <th
                onClick={() => handleSort('marginPercent')}
                className="py-3 px-3.5 text-right cursor-pointer hover:bg-slate-200/60 transition-colors"
              >
                <div className="flex items-center justify-end space-x-1">
                  <span>Маржа</span>
                  <ArrowUpDown className="w-3 h-3 text-slate-400" />
                </div>
              </th>
            </tr>
          </thead>

          {/* Table Body */}
          <tbody className="divide-y divide-slate-100 text-slate-700">
            {pageRows.map((row, idx) => (
              <tr
                key={row.date}
                className={`hover:bg-slate-50/90 transition-colors ${
                  idx % 2 === 1 ? 'bg-slate-50/30' : 'bg-white'
                }`}
              >
                <td className="py-2.5 px-3.5 font-medium text-slate-900">
                  {row.fullDateStr}
                </td>
                <td className="py-2.5 px-3.5 text-right text-slate-800">
                  {formatCurrency(row.sales)}
                </td>
                <td className="py-2.5 px-3.5 text-right text-rose-600 font-medium">
                  {formatCurrency(row.returns)}
                </td>
                <td className="py-2.5 px-3.5 text-right text-slate-800">
                  {formatCurrency(row.partnerPrograms)}
                </td>
                <td className="py-2.5 px-3.5 text-right font-bold text-blue-700 bg-blue-50/20">
                  {formatCurrency(row.income)}
                </td>
                <td className="py-2.5 px-3.5 text-right text-slate-600">
                  {formatCurrency(row.commission)}
                </td>
                <td className="py-2.5 px-3.5 text-right text-slate-600">
                  {formatCurrency(row.logistics)}
                </td>
                <td className="py-2.5 px-3.5 text-right text-slate-600">
                  {formatCurrency(row.otherExpenses)}
                </td>
                <td className="py-2.5 px-3.5 text-right font-bold text-rose-600 bg-rose-50/20">
                  {formatCurrency(row.totalExpenses)}
                </td>
                <td className="py-2.5 px-3.5 text-right font-bold text-emerald-600 bg-emerald-50/20">
                  {formatCurrency(row.profit)}
                </td>
                <td className="py-2.5 px-3.5 text-right font-semibold text-slate-700">
                  {row.marginPercent}%
                </td>
              </tr>
            ))}
          </tbody>

          {/* Sticky Table Footer (Итого) - Guaranteed not to scroll away vertically */}
          <tfoot className="sticky bottom-0 bg-slate-100/98 backdrop-blur-md text-slate-900 font-bold z-20 border-t-2 border-slate-300 shadow-md">
            <tr>
              <td className="py-3 px-3.5 text-slate-900 font-black">
                Итого за период:
              </td>
              <td className="py-3 px-3.5 text-right">
                {formatCurrency(totals.sales)}
              </td>
              <td className="py-3 px-3.5 text-right text-rose-600">
                {formatCurrency(totals.returns)}
              </td>
              <td className="py-3 px-3.5 text-right">
                {formatCurrency(totals.partnerPrograms)}
              </td>
              <td className="py-3 px-3.5 text-right text-blue-700 bg-blue-100/60 font-black">
                {formatCurrency(totals.income)}
              </td>
              <td className="py-3 px-3.5 text-right">
                {formatCurrency(totals.commission)}
              </td>
              <td className="py-3 px-3.5 text-right">
                {formatCurrency(totals.logistics)}
              </td>
              <td className="py-3 px-3.5 text-right">
                {formatCurrency(totals.otherExpenses)}
              </td>
              <td className="py-3 px-3.5 text-right text-rose-700 bg-rose-100/60 font-black">
                {formatCurrency(totals.totalExpenses)}
              </td>
              <td className="py-3 px-3.5 text-right text-emerald-700 bg-emerald-100/60 font-black">
                {formatCurrency(totals.profit)}
              </td>
              <td className="py-3 px-3.5 text-right font-black">
                {totalMarginPercent}%
              </td>
            </tr>
          </tfoot>
        </table>
      </div>

      {/* Pagination Footer (Bottom Right requirement) */}
      <div className="flex items-center justify-between pt-2 px-1 text-xs text-slate-500">
        <div>
          Показано <span className="font-semibold text-slate-700">{pageRows.length ? startIndex + 1 : 0}</span>
          –
          <span className="font-semibold text-slate-700">
            {Math.min(startIndex + pageSize, sortedData.length)}
          </span>{' '}
          из <span className="font-semibold text-slate-700">{sortedData.length}</span> дней
        </div>

        {/* Page selector: "страница 1 из n и стрелочки" */}
        <div className="flex items-center space-x-3">
          <span className="font-medium text-slate-700">
            Страница {pageIndex} из {totalPages}
          </span>
          <div className="flex items-center space-x-1">
            <button
              onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
              disabled={pageIndex <= 1}
              className="p-1 rounded-lg border border-slate-200 hover:bg-slate-100 text-slate-600 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              title="Предыдущая страница"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button
              onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
              disabled={pageIndex >= totalPages}
              className="p-1 rounded-lg border border-slate-200 hover:bg-slate-100 text-slate-600 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              title="Следующая страница"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
