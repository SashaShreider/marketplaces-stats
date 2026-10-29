import { useCallback, useEffect, useRef, useState } from 'react'
import type { DateRange } from '@/types'

/**
 * Состояние экрана в адресной строке.
 *
 * <p>Период, страница и отбор — это условия вопроса, и они должны переживать
 * перезагрузку: без них F5 возвращал на «Обзор» с периодом по умолчанию, хотя
 * человек ничего не менял. Здесь же ссылку на «эти товары за этот месяц» можно
 * просто переслать, как в кабинетах маркетплейсов.
 *
 * <p>В адрес попадает только то, что меняет сам вопрос. Критерий сортировки,
 * поиск и номер страницы списка — способ прочитать тот же отчёт, и держать их в
 * ссылке значило бы менять адрес на каждый клик по заголовку таблицы.
 *
 * <p>Адрес — единственный источник правды при первом рендере, дальше значение
 * живёт в состоянии и записывается в адрес через `replaceState` (условия отбора)
 * или `pushState` (переход между разделами, чтобы работала кнопка «Назад»).
 */

/** Что записать в адрес: `null` или пустая строка — параметр убрать. */
export type UrlPatch = Record<string, string | string[] | null | undefined>

type SetValue<T> = (value: T | ((prev: T) => T)) => void

function currentSearch(): URLSearchParams {
  return new URLSearchParams(window.location.search)
}

/**
 * Дописывает параметры в текущий адрес, не трогая остальные.
 *
 * <p>Читаем адрес заново перед записью: за один обработчик в адрес пишут
 * несколько полей (период и отбор), и запись «из снимка» затёрла бы то, что
 * записало предыдущее поле.
 */
export function writeParams(patch: UrlPatch, mode: 'replace' | 'push' = 'replace') {
  const params = currentSearch()
  for (const [key, value] of Object.entries(patch)) {
    params.delete(key)
    if (value === null || value === undefined || value === '') continue
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item !== '') params.append(key, item)
      }
    } else {
      params.set(key, value)
    }
  }
  const query = params.toString()
  const url = `${window.location.pathname}${query ? `?${query}` : ''}${window.location.hash}`
  if (mode === 'push') window.history.pushState(null, '', url)
  else window.history.replaceState(null, '', url)
}

/**
 * Значение экрана, связанное с адресной строкой.
 *
 * @param parse  как прочитать значение из параметров; вызывается при монтировании
 *               и при переходах по истории
 * @param format какие параметры описывают значение
 * @param push   писать ли новый шаг истории вместо замены текущего
 * @param writeOnMount записать значение в адрес сразу при монтировании. Нужно
 *               для значений, у которых есть умолчание: без записи адрес молчит
 *               о том, что применилось, и ссылку нельзя переслать.
 */
export function useUrlState<T>(
  parse: (params: URLSearchParams) => T,
  format: (value: T) => UrlPatch,
  { push = false, writeOnMount = false }: { push?: boolean; writeOnMount?: boolean } = {},
): [T, SetValue<T>] {
  // Читатели и писатели приходят литералами на каждый рендер; в колбэках нужны
  // только свежие, поэтому держим их в ref, а не в зависимостях.
  const parseRef = useRef(parse)
  parseRef.current = parse
  const formatRef = useRef(format)
  formatRef.current = format

  const read = useCallback(() => parseRef.current(currentSearch()), [])

  const [value, setValue] = useState<T>(read)
  // Значение для функциональных обновлений: React может вызвать такой updater
  // дважды, и побочная запись в адрес внутри него случилась бы тоже дважды.
  const latest = useRef(value)
  latest.current = value

  const set = useCallback<SetValue<T>>(
    (next) => {
      const resolved = typeof next === 'function' ? (next as (prev: T) => T)(latest.current) : next
      latest.current = resolved
      setValue(resolved)
      writeParams(formatRef.current(resolved), push ? 'push' : 'replace')
    },
    [push],
  )

  // Кнопки «Назад»/«Вперёд» меняют адрес мимо нас — значение надо перечитать.
  useEffect(() => {
    const sync = () => {
      const next = read()
      latest.current = next
      setValue(next)
    }
    window.addEventListener('popstate', sync)
    return () => window.removeEventListener('popstate', sync)
  }, [read])

  // Умолчание тоже попадает в адрес: иначе адрес молчал бы о том, какой период
  // показан, и ссылка на текущий экран была бы неполной.
  const wroteOnMount = useRef(false)
  useEffect(() => {
    if (!writeOnMount || wroteOnMount.current) return
    wroteOnMount.current = true
    writeParams(formatRef.current(latest.current), 'replace')
  }, [writeOnMount])

  return [value, set]
}

// ─── Даты ────────────────────────────────────────────────────────────────────

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/
const RU_DATE = /^(\d{2})\.(\d{2})\.(\d{4})$/

/**
 * Дата из адреса: `2026-09-01` или `01.09.2026`.
 *
 * <p>Второй формат принимается потому, что так даты пишут похожие кабинеты и так
 * их копируют из переписки. В адрес мы пишем только ISO: он сортируется и не
 * зависит от того, что человек считает первым — день или месяц.
 */
export function parseDateParam(raw: string | null): string | null {
  if (!raw) return null
  const value = raw.trim()
  if (ISO_DATE.test(value)) return value
  const ru = RU_DATE.exec(value)
  if (!ru) return null
  const [, day, month, year] = ru
  return `${year}-${month}-${day}`
}

/**
 * Период из адреса. `null`, если он задан не полностью или нечитаемо: половина
 * периода — это не период, и подставлять к ней сегодняшнюю дату значило бы
 * показать не тот отчёт, который просили.
 */
export function readRangeParam(params: URLSearchParams): DateRange | null {
  const from = parseDateParam(params.get('d1'))
  const to = parseDateParam(params.get('d2'))
  if (!from || !to) return null
  return from <= to ? { from, to } : { from: to, to: from }
}
