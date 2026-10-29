import { fmtMoney2, fmtPercent } from '@/utils/format'

/**
 * Кнопка, кнопка-ссылка и поле ввода — общие для модалок и фильтров.
 *
 * <p>Вынесены, потому что три окна «Обзора» повторяли одни и те же классы: копии
 * расходились по отступам и цвету состояния, и «Сохранить» в одном окне выглядело
 * чужим в другом.
 */

export function PrimaryButton({
  children,
  onClick,
  disabled,
  type = 'button',
}: {
  children: React.ReactNode
  onClick?: () => void
  disabled?: boolean
  type?: 'button' | 'submit'
}) {
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className="flex items-center justify-center gap-1.5 rounded-xl bg-brand-600 px-4 py-2 text-[12.5px] font-bold text-white shadow-md shadow-brand-600/25 transition-all hover:bg-brand-700 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50"
    >
      {children}
    </button>
  )
}

export function SecondaryButton({
  children,
  onClick,
  disabled,
  title,
}: {
  children: React.ReactNode
  onClick?: () => void
  disabled?: boolean
  title?: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title}
      className="flex items-center justify-center gap-1.5 rounded-xl border border-slate-200 px-3.5 py-2 text-[12.5px] font-bold text-slate-600 transition-all hover:border-slate-300 hover:text-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
    >
      {children}
    </button>
  )
}

/**
 * Строка «подпись — значение» для расшифровок.
 *
 * <p>Суммы моноширинные и с копейками: расшифровка обязана складываться в итог, и
 * округлённые до рубля слагаемые этого не делают.
 *
 * @param share процент от базы; `undefined` — процент не считается
 */
export function DetailRow({
  label,
  hint,
  amount,
  share,
  strong,
}: {
  label: string
  hint?: string | null
  amount: number
  share?: number
  strong?: boolean
}) {
  return (
    <div className="flex items-baseline justify-between gap-3 border-b border-slate-100 py-2 last:border-0">
      <div className="min-w-0">
        <div
          className={
            strong
              ? 'text-[13px] font-extrabold text-slate-900'
              : 'text-[12.5px] font-semibold text-slate-700'
          }
        >
          {label}
        </div>
        {hint && (
          <div className="mt-0.5 truncate text-[11px] font-medium text-slate-400" title={hint}>
            {hint}
          </div>
        )}
      </div>
      <div className="flex shrink-0 items-baseline gap-3">
        {share !== undefined && (
          <span className="tnum text-[11.5px] font-semibold text-slate-400">
            {fmtPercent(share)}
          </span>
        )}
        <span
          className={
            strong
              ? 'tnum text-[13px] font-extrabold text-slate-900'
              : 'tnum text-[12.5px] font-bold text-slate-700'
          }
        >
          {/* Суммы расходов приходят отрицательными; модуль со знаком минус
              показывает их одинаково и когда начисление — расход, и когда оно
              вернулось плюсом: знак остаётся тем, что отдал маркетплейс. */}
          {amount < 0 ? '−' : amount > 0 ? '+' : ''}
          {fmtMoney2(Math.abs(amount))}
        </span>
      </div>
    </div>
  )
}
