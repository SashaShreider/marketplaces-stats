import { useEffect, useRef, useState } from 'react'
import { cn } from '@/utils/cn'
import { fmtNum } from '@/utils/format'

/**
 * Плавно «докручивающееся» число.
 *
 * <p>Значение меняется часто — импорт идёт, цифры подтягиваются пачками — и без
 * плавности таблица дёргалась бы на каждом обновлении, мешая читать.
 */
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

/** Денежное или иное число, которое плавно пересчитывается при изменении. */
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