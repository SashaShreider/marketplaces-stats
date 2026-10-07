import { cn } from '@/utils/cn'

/**
 * Значок маркетплейса.
 *
 * <p>Цвета и начертание повторяют бренд, но картинки у нас нет: держать в репозитории
 * логотипы чужих компаний ради одного значка — лишняя лицензионная возня. Код
 * приходит из API, неизвестный маркетплейс показывается первыми двумя буквами.
 */
export function MarketplaceLogo({
  code,
  size = 'md',
}: {
  code: string
  size?: 'sm' | 'md' | 'lg'
}) {
  const s = {
    sm: 'h-5 w-5 rounded-[6px] text-[8px]',
    md: 'h-6 w-6 rounded-lg text-[9px]',
    lg: 'h-10 w-10 rounded-xl text-[13px]',
  }[size]
  const c = code.toUpperCase()

  if (c === 'OZON') {
    return (
      <span
        className={cn(
          s,
          'inline-flex shrink-0 items-center justify-center bg-[#005BFF] font-extrabold lowercase tracking-tight text-white',
        )}
      >
        ozon
      </span>
    )
  }
  if (c === 'WB' || c.startsWith('WILDBERRIES')) {
    return (
      <span
        className={cn(
          s,
          'inline-flex shrink-0 items-center justify-center bg-gradient-to-br from-[#CB11AB] via-[#A020F0] to-[#481173] font-extrabold uppercase text-white',
        )}
      >
        WB
      </span>
    )
  }
  return (
    <span
      className={cn(
        s,
        'inline-flex shrink-0 items-center justify-center bg-slate-700 font-extrabold uppercase text-white',
      )}
    >
      {c.slice(0, 2)}
    </span>
  )
}