import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { X } from 'lucide-react'
import { cn } from '@/utils/cn'

/**
 * Окно поверх страницы.
 *
 * <p>Общее для всех разборов на «Обзоре»: расшифровка статьи расходов, детализация
 * расходов целиком, таблица по дням. Три копии затемнения и обработчика Escape
 * разъехались бы по поведению: в одной закрытие по фону работало бы, в другой нет.
 *
 * <p>Рисуется через портал в {@code body}, а не там, где стоит в разметке.
 * Окно вложено в карточку, а карточку страница при загрузке накрывает
 * `opacity-60` — и предок с прозрачностью делает полупрозрачным всё содержимое,
 * включая окно: под его шапкой просвечивала страница. Портал выводит окно из
 * этого поддерева, и прозрачность предка на него уже не действует.
 *
 * <p>Прокрутка тела страницы блокируется, пока окно открыто: иначе колесо мыши
 * прокручивало бы список под окном, и человек терял место, на котором читал.
 *
 * @param size   ширина окна; `xl` — широкое под таблицы, `full` — почти весь экран
 * @param footer необязательная полоса действий внизу
 */
export default function Modal({
  open,
  title,
  subtitle,
  headerExtra,
  headerBelow,
  onClose,
  size = 'md',
  footer,
  children,
}: {
  open: boolean
  title: string
  subtitle?: React.ReactNode
  /**
   * Управление справа от заголовка — фильтры и переключатели вида.
   *
   * <p>Слот, а не `children`: содержимое окна прокручивается, и фильтры уехали бы
   * вместе с ним. Условия просмотра должны оставаться на месте, как и заголовок.
   */
  headerExtra?: React.ReactNode
  /**
   * Управление под строкой заголовка, слева.
   *
   * <p>Отдельный слот от {@link headerExtra}: в строке с названием помещается
   * только компактное управление, а фильтры с легендой там ломали бы заголовок.
   * Что куда положить, решает вызывающий.
   */
  headerBelow?: React.ReactNode
  onClose: () => void
  size?: 'md' | 'lg' | 'xl' | 'full'
  footer?: React.ReactNode
  children: React.ReactNode
}) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onKey)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', onKey)
    }
  }, [open, onClose])

  const width =
    size === 'full'
      ? 'w-[calc(100vw-2rem)] max-w-[1800px]'
      : size === 'xl'
        ? 'w-[calc(100vw-3rem)] max-w-[1640px]'
        : size === 'lg'
          ? 'w-full max-w-[760px]'
          : 'w-full max-w-[520px]'

  return createPortal(
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.16 }}
            onClick={onClose}
            // Размытие фона только у небольших окон. У широких под ними лежит
            // таблица на сотню строк, и `backdrop-blur` пересобирает этот слой при
            // каждой перерисовке — фильтры в шапке окна на секунду «вешали»
            // страницу. Затемнение без размытия читается так же.
            className={cn(
              'absolute inset-0 bg-slate-900/40',
              size === 'xl' || size === 'full' ? '' : 'backdrop-blur-sm',
            )}
          />
          <motion.div
            role="dialog"
            aria-modal="true"
            aria-label={title}
            // Широкое окно появляется только по прозрачности.
            //
            // <p>`scale` и `y` оставляют на корне окна `transform`, а любой
            // `transform` на предке ломает `position: sticky` у потомков: шапка
            // таблицы перестаёт липнуть и уезжает вместе с данными. Внутри `xl`
            // живёт таблица на сотню строк, и ей шапка нужнее, чем анимация.
            initial={size === 'xl' ? { opacity: 0 } : { opacity: 0, scale: 0.97, y: 12 }}
            animate={size === 'xl' ? { opacity: 1 } : { opacity: 1, scale: 1, y: 0 }}
            exit={size === 'xl' ? { opacity: 0 } : { opacity: 0, scale: 0.98, y: 8 }}
            transition={
              size === 'xl'
                ? { duration: 0.15 }
                : { type: 'spring', stiffness: 380, damping: 32 }
            }
            className={cn(
              'relative flex max-h-[calc(100vh-2rem)] flex-col overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-2xl shadow-slate-900/20',
              width,
            )}
          >
            <div className="border-b border-slate-100 px-5 py-3">
              {/* Управление стоит в одной строке с названием: так условие отчёта
                  читается вместе с тем, что оно ограничивает. Строка переносится,
                  если места не хватило, а крестик всегда остаётся справа. */}
              <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
                <div className="min-w-0">
                  <h3 className="text-[16px] font-extrabold tracking-tight text-slate-900">{title}</h3>
                  {subtitle && (
                    <div className="mt-0.5 text-[12.5px] font-medium leading-relaxed text-slate-500">
                      {subtitle}
                    </div>
                  )}
                </div>
                {headerExtra && <div className="min-w-0">{headerExtra}</div>}
                <button
                  onClick={onClose}
                  title="Закрыть"
                  className="ml-auto flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
                >
                  <X size={16} />
                </button>
              </div>
              {headerBelow && <div className="mt-3 min-w-0">{headerBelow}</div>}
            </div>

            <div className="scroll-slim min-h-0 flex-1 overflow-auto px-5 py-4">{children}</div>

            {footer && (
              <div className="flex items-center gap-2 border-t border-slate-100 px-5 py-3">
                {footer}
              </div>
            )}
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  )
}
