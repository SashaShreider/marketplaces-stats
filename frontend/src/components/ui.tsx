import { useEffect, useRef, useState } from 'react'
import { cn } from '../utils/cn'
import { fmtNum } from '../utils/format'

// ─── Плавно «докручивающееся» число ──────────────────────────────────────────

export function useCountUp(value: number, duration = 750): number {
  const [display, setDisplay] = useState(value)
  const fromRef = useRef(value)
  const rafRef = useRef<number>(0)

  useEffect(() => {
    const from = fromRef.current
    if (from === value) return
    const start = performance.now()
    const tick = (t: number) => {
      const p = Math.min(1, (t - start) / duration)
      const e = 1 - Math.pow(1 - p, 3)
      const cur = from + (value - from) * e
      fromRef.current = cur
      setDisplay(cur)
      if (p < 1) rafRef.current = requestAnimationFrame(tick)
    }
    rafRef.current = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(rafRef.current)
  }, [value, duration])

  return display
}

export function AnimatedNumber({
  value,
  suffix = ' ₽',
  className,
}: {
  value: number
  suffix?: string
  className?: string
}) {
  const v = useCountUp(value)
  return (
    <span className={cn('tnum', className)}>
      {fmtNum(v)}
      {suffix}
    </span>
  )
}

// ─── Логотип маркетплейса (по коду из API) ───────────────────────────────────

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

// ─── Заголовок страницы ──────────────────────────────────────────────────────

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
