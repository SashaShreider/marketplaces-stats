/**
 * Заголовок страницы: название, пояснение и место под действия справа.
 *
 * <p>Место под действия (`children`) — это то, что относится ко всему периоду:
 * календарь и состояние загрузки. Поэтому шапка отдаёт его выровненным вправо, и
 * вся работа со страницей по времени собирается в одном месте рядом с датами.
 */
export function PageHeader({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle: React.ReactNode
  children?: React.ReactNode
}) {
  return (
    <div className="mb-5 flex flex-wrap items-end gap-x-6 gap-y-4">
      <div>
        <h1 className="text-[28px] font-extrabold tracking-tight text-slate-900">{title}</h1>
        <p className="mt-0.5 flex items-center gap-2 text-[13.5px] font-medium text-slate-500">
          {subtitle}
        </p>
      </div>
      {children && <div className="ml-auto">{children}</div>}
    </div>
  )
}