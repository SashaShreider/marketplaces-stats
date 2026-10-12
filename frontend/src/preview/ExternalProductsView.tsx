// ─── Предпросмотр компонента из другого проекта ─────────────────────────────
// Файл перенесён как есть из D:\Users\sasha\Desktop\components\ProductsView.tsx.
// Изменены только импорты под этот проект: типы и форматтеры. Разметка, цвета и
// поведение — оригинальные, иначе сравнивать было бы не с чем. Правки в
// оформлении сюда вносить не стоит.

import React, { useState } from 'react';
import { Package, Search, Star, ExternalLink, ArrowUpDown } from 'lucide-react';
import { ProductItem, MarketplaceId } from './types';
import { fmtMoney as formatCurrency, fmtNum as formatNumber } from '@/utils/format';

interface ProductsViewProps {
  products: ProductItem[];
  currentMarketplaceId: MarketplaceId;
}

export const ProductsView: React.FC<ProductsViewProps> = ({
  products,
  currentMarketplaceId,
}) => {
  const [search, setSearch] = useState('');
  const [sortKey, setSortKey] = useState<keyof ProductItem>('revenue');
  const [sortAsc, setSortAsc] = useState(false);

  const filtered = products
    .filter((p) => p.marketplace === currentMarketplaceId)
    .filter((p) =>
      p.title.toLowerCase().includes(search.toLowerCase()) ||
      p.sku.toLowerCase().includes(search.toLowerCase()) ||
      p.category.toLowerCase().includes(search.toLowerCase())
    )
    .sort((a, b) => {
      const valA = a[sortKey];
      const valB = b[sortKey];
      if (typeof valA === 'string') {
        return sortAsc ? (valA as string).localeCompare(valB as string) : (valB as string).localeCompare(valA as string);
      }
      return sortAsc ? (valA as number) - (valB as number) : (valB as number) - (valA as number);
    });

  const handleSort = (key: keyof ProductItem) => {
    if (sortKey === key) {
      setSortAsc(!sortAsc);
    } else {
      setSortKey(key);
      setSortAsc(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Товары и остатки</h1>
          <p className="text-sm text-slate-500 mt-0.5">
            Аналитика по SKU для магазина на {currentMarketplaceId === 'ozon' ? 'Ozon' : 'Wildberries'}
          </p>
        </div>

        <div className="relative w-full sm:w-72">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Поиск по артикулу, названию..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-9 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
          />
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs whitespace-nowrap">
            <thead className="bg-slate-50 text-slate-600 font-semibold border-b border-slate-200">
              <tr>
                <th className="py-3.5 px-4 cursor-pointer" onClick={() => handleSort('title')}>
                  <div className="flex items-center space-x-1">
                    <span>Товар / Артикул</span>
                    <ArrowUpDown className="w-3 h-3 text-slate-400" />
                  </div>
                </th>
                <th className="py-3.5 px-4 cursor-pointer" onClick={() => handleSort('category')}>
                  <div className="flex items-center space-x-1">
                    <span>Категория</span>
                    <ArrowUpDown className="w-3 h-3 text-slate-400" />
                  </div>
                </th>
                <th className="py-3.5 px-4 text-right cursor-pointer" onClick={() => handleSort('price')}>
                  <div className="flex items-center justify-end space-x-1">
                    <span>Цена</span>
                    <ArrowUpDown className="w-3 h-3 text-slate-400" />
                  </div>
                </th>
                <th className="py-3.5 px-4 text-right cursor-pointer" onClick={() => handleSort('ordersCount')}>
                  <div className="flex items-center justify-end space-x-1">
                    <span>Заказы</span>
                    <ArrowUpDown className="w-3 h-3 text-slate-400" />
                  </div>
                </th>
                <th className="py-3.5 px-4 text-right cursor-pointer bg-blue-50/50" onClick={() => handleSort('revenue')}>
                  <div className="flex items-center justify-end space-x-1 text-blue-900 font-bold">
                    <span>Выручка</span>
                    <ArrowUpDown className="w-3 h-3 text-blue-600" />
                  </div>
                </th>
                <th className="py-3.5 px-4 text-right cursor-pointer" onClick={() => handleSort('stock')}>
                  <div className="flex items-center justify-end space-x-1">
                    <span>Остаток</span>
                    <ArrowUpDown className="w-3 h-3 text-slate-400" />
                  </div>
                </th>
                <th className="py-3.5 px-4 text-center cursor-pointer" onClick={() => handleSort('rating')}>
                  <div className="flex items-center justify-center space-x-1">
                    <span>Рейтинг</span>
                    <ArrowUpDown className="w-3 h-3 text-slate-400" />
                  </div>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700">
              {filtered.map((item) => (
                <tr key={item.id} className="hover:bg-slate-50 transition-colors">
                  <td className="py-3 px-4">
                    <div className="font-semibold text-slate-900 text-sm">{item.title}</div>
                    <div className="text-slate-400 font-mono text-[11px] mt-0.5 flex items-center space-x-1">
                      <span>SKU: {item.sku}</span>
                      <ExternalLink className="w-3 h-3 cursor-pointer hover:text-blue-600" />
                    </div>
                  </td>
                  <td className="py-3 px-4">
                    <span className="px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-medium">
                      {item.category}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right font-medium text-slate-800">
                    {formatCurrency(item.price)}
                  </td>
                  <td className="py-3 px-4 text-right font-semibold text-slate-800">
                    {formatNumber(item.ordersCount)} шт
                  </td>
                  <td className="py-3 px-4 text-right font-bold text-blue-700 bg-blue-50/30">
                    {formatCurrency(item.revenue)}
                  </td>
                  <td className="py-3 px-4 text-right">
                    <span
                      className={`font-semibold ${
                        item.stock < 30 ? 'text-amber-600' : 'text-slate-800'
                      }`}
                    >
                      {item.stock} шт
                    </span>
                  </td>
                  <td className="py-3 px-4 text-center">
                    <div className="inline-flex items-center space-x-1 text-amber-500 font-semibold">
                      <Star className="w-3.5 h-3.5 fill-amber-400" />
                      <span className="text-slate-700">{item.rating}</span>
                    </div>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={7} className="text-center py-12 text-slate-400">
                    <Package className="w-8 h-8 mx-auto mb-2 opacity-40" />
                    <div>Товары не найдены</div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
