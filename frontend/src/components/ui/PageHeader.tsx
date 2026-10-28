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
  /** Необязательное пояснение: на «Обзоре» всё нужное уже в строке фильтров. */
  subtitle?: React.ReactNode
  children?: React.ReactNode
}) {
  return (
    <div className="mb-3.5 flex flex-wrap items-end gap-x-6 gap-y-3">
      <div>
        <h1 className="text-[24px] font-extrabold leading-tight tracking-tight text-slate-900">{title}</h1>
        {subtitle && (
          <p className="mt-0.5 flex items-center gap-2 text-[13px] font-medium text-slate-500">
            {subtitle}
          </p>
        )}
      </div>
      {children && <div className="ml-auto">{children}</div>}
    </div>
  )
}