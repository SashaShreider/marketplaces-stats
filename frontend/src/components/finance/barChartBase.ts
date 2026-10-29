import { useLayoutEffect, useState, type RefObject } from 'react'
import { clamp } from '@/utils/format'

/**
 * Общая часть графиков: ширина блока, шкалы и место для тултипа.
 *
 * <p>График денег на «Обзоре» и график продаж в штуках устроены по-разному — это
 * разные метрики, разные подписи и разные тултипы. Общими у них только три вещи:
 * как измеряется ширина блока, как считаются координаты и куда встаёт тултип.
 * Вот эти три вещи и вынесены сюда, чтобы не расходились при правке одного
 * графика и не забыли поправить другой.
 */

/** Отступы от краёв рисунка. */
export interface ChartPadding {
  left: number
  right: number
  top: number
  bottom: number
}

/** Шкалы графика: координаты и сетка. */
export interface ChartScales {
  /** Позиция дня по горизонтали, по его номеру. */
  x: (index: number) => number
  /** Позиция значения по вертикали. */
  y: (value: number) => number
  /** Ширина одного дня. */
  slot: number
  /** Ширина и высота поля рисования. */
  innerW: number
  innerH: number
  /** Линии сетки: от нуля до верха шкалы. */
  gridLines: number[]
}

/**
 * Ширина блока, за которой следит наблюдатель.
 *
 * <p>График обязан тянуться за шириной блока, а не сидеть на заранее заданной:
 * на телефоне или в узкой колонке он иначе обрезается.
 */
export function useChartWidth(ref: RefObject<HTMLElement | null>): number {
  const [width, setWidth] = useState(760)

  useLayoutEffect(() => {
    const element = ref.current
    if (!element) return
    const observer = new ResizeObserver((entries) => {
      const w = entries[0].contentRect.width
      if (w > 0) setWidth(w)
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [ref])

  return width
}

/**
 * Шкалы графика по его ширине, высоте и границам значений.
 *
 * <p>`minValue` не нулевой, когда данные уходят в минус: у прибыли убыточный день
 * — обычное дело, и без отрицательной части шкалы такая точка прижималась к нулю
 * и читалась как «profit = 0». Значение при этом зажимается в границы, чтобы
 * линия не выходила за поле рисунка.
 *
 * <p>`gridLines` не считается здесь: шаг сетки задаёт тот, кто знает, круглые ли
 * ему нужны числа (`niceScale` в `utils/format`). Здесь только координаты.
 */
export function chartScales(options: {
  count: number
  width: number
  height: number
  pad: ChartPadding
  maxValue: number
  minValue?: number
  gridLines?: number[]
}): ChartScales {
  const { count, width, height, pad, maxValue, minValue = 0, gridLines } = options
  const innerW = Math.max(10, width - pad.left - pad.right)
  const innerH = height - pad.top - pad.bottom
  const slot = innerW / Math.max(1, count)
  const range = maxValue - minValue || 1

  return {
    x: (index: number) => pad.left + slot * (index + 0.5),
    y: (value: number) =>
      pad.top + innerH * (1 - (clamp(value, minValue, maxValue) - minValue) / range),
    slot,
    innerW,
    innerH,
    // Без явных линий сетки — пять равных промежутков, как было раньше: график
    // продаж в штуках на них и рассчитан.
    gridLines:
      gridLines ??
      [0, 0.25, 0.5, 0.75, 1].map((fraction) => minValue + fraction * (maxValue - minValue)),
  }
}

/**
 * Место тултипа: сбоку от точки, но внутри графика.
 *
 * <p>Тултип не должен перекрывать точку, на которую навели, и не должен вылезать
 * за правый край — иначе он либо закрывает значение, либо обрезается.
 */
export function tooltipPosition(options: {
  hoverX: number
  hoverY: number
  width: number
  height: number
  tipWidth: number
  tipHeight: number
  /** Отступ от точки до тултипа. */
  offset: number
  padRight: number
}): { left: number; top: number } {
  const { hoverX, hoverY, width, height, tipWidth, tipHeight, offset, padRight } = options
  const onRight = hoverX + offset + tipWidth < width - padRight
  const left = onRight
    ? Math.min(hoverX + offset, width - tipWidth - 4)
    : Math.max(hoverX - offset - tipWidth, 4)
  const top = clamp(hoverY - tipHeight / 2, 4, height - tipHeight - 4)
  return { left, top }
}
