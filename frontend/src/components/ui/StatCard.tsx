import { motion } from 'framer-motion'
import { Info } from 'lucide-react'
import { cn } from '@/utils/cn'

/**
 * Значок с подсказкой при наведении.
 *
 * <p>Подсказка на CSS, а не на состоянии: наведение мыши не должно вызывать
 * перерисовку страницы, а появление и скрытие подсказки не должно требовать
 * обработчиков, которые нужно не забыть снять.
 */
export function Hint({ text }: { text: string }) {
  return (
    <span className="group/hint relative inline-flex align-middle">
      <Info
        size={13}
        className="cursor-help text-slate-400 transition-colors group-hover/hint:text-slate-600"
      />
      <span
        role="tooltip"
        className="pointer-events-none absolute bottom-full left-1/2 z-20 mb-2 w-64 -translate-x-1/2 rounded-lg bg-slate-900 px-3 py-2 text-[12px] font-medium normal-case leading-relaxed tracking-normal text-white opacity-0 shadow-lg transition-opacity duration-150 group-hover/hint:opacity-100"
      >
        {text}
      </span>
    </span>
  )
}

/**
 * Карточка с числом.
 *
 * <p>Одна на обзор и на страницу товаров: раньше раздельные копии разъезжались
 * по отступам и размеру шрифта, и страницы выглядели по-разному при одинаковом
 * смысле. Размер задаётся явно, чтобы крупная карточка обзора и компактная
 * карточка товаров остались такими, какими были.
 */
export default function StatCard({
  label,
  hint,
  icon,
  iconBg,
  value,
  sub,
  children,
  tone,
  delay,
  size = 'md',
  className,
}: {
  label: string
  /** Объяснение подписи, если её нельзя прочитать буквально */
  hint?: string
  /** Значок рядом с подписью — только для крупной карточки */
  icon?: React.ReactNode
  /** Подложка значка */
  iconBg?: string
  /** Само число или любой узел вместо него */
  value: React.ReactNode
  /** Подпись под числом */
  sub?: React.ReactNode
  /** Дополнительное содержимое под числом — только для крупной карточки */
  children?: React.ReactNode
  tone: string
  /** Задержка появления, в секундах */
  delay: number
  size?: 'md' | 'lg'
  /** Место карточки в сетке: широкие карточки занимают две колонки */
  className?: string
}) {
  const large = size === 'lg'
  return (
    <motion.div
      initial={{ opacity: 0, y: large ? 14 : 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={
        large
          ? { duration: 0.45, delay, ease: [0.22, 1, 0.36, 1] }
          : { duration: 0.4, delay }
      }
      className={cn('rounded-2xl border p-4', tone, className)}
    >
      <div className="flex items-center gap-1.5">
        {icon && (
          <span className={cn('flex h-8 w-8 items-center justify-center rounded-xl', iconBg)}>
            {icon}
          </span>
        )}
        <span className="text-[11px] font-extrabold uppercase tracking-[0.1em] text-slate-500">
          {label}
        </span>
        {hint && <Hint text={hint} />}
      </div>
      <div
        className={cn(
          'font-extrabold leading-none tracking-tight text-slate-900',
          large ? 'mt-2.5 text-[26px]' : 'mt-2 text-[24px]',
        )}
      >
        {value}
      </div>
      {sub && <div className="mt-2 text-[12px] font-medium text-slate-500">{sub}</div>}
      {children && <div className={large ? 'mt-3' : undefined}>{children}</div>}
    </motion.div>
  )
}
