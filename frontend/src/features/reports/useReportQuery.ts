import { useCallback, useEffect, useRef, useState } from 'react'
import { describeError } from '@/api/http'
import type { ImportProgress } from '@/types'

/**
 * Ключ прогресса импорта.
 *
 * <p>По нему видно, что данные изменились и отчёт пора перечитать. Ключ включает
 * и пропущенные единицы: раньше страница товаров их не учитывала и потому
 * молчала о том, что день загрузили целиком заново.
 */
export function importProgressKey(run: ImportProgress | null): string {
  if (!run) return ''
  return `${run.id}:${run.doneUnits}:${run.skippedUnits}:${run.failedUnits}:${run.inProgress}`
}

export interface ReportQuery<T> {
  /** Отчёт; `null`, пока он не пришёл */
  data: T | null
  loading: boolean
  error: string | null
  /** Перечитать отчёт. `silent` — не показывать «загрузку» поверх прежних данных. */
  reload: (silent?: boolean) => void
}

/**
 * Загрузка отчёта с защитой от гонок.
 *
 * <p>Общий хук для страниц с отчётом: он сам следит за тем, чтобы показывался
 * ответ последнего запроса, а не запоздалый, и сам перечитывает отчёт, когда
 * изменился прогресс импорта.
 *
 * <p>Страница передаёт `fetch` через `useCallback`: смена его идентичности
 * означает, что условия запроса изменились и отчёт надо перечитать.
 */
export function useReportQuery<T>(options: {
  fetch: () => Promise<T>
  /** Ключ прогресса импорта; пустая строка — не следить за прогрессом */
  progressKey?: string
}): ReportQuery<T> {
  const { fetch, progressKey = '' } = options
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const token = useRef(0)

  const reload = useCallback(
    async (silent = false) => {
      const t = ++token.current
      if (!silent) setLoading(true)
      try {
        const result = await fetch()
        if (token.current !== t) return
        setData(result)
        setError(null)
      } catch (e) {
        if (token.current !== t) return
        setError(describeError(e))
      } finally {
        if (token.current === t) setLoading(false)
      }
    },
    [fetch],
  )

  // условия запроса изменились — отчёт надо перечитать
  useEffect(() => {
    reload()
  }, [reload])

  // по ходу импорта подтягиваем свежие цифры (результат доступен до окончания импорта)
  const lastKey = useRef(progressKey)
  useEffect(() => {
    if (!progressKey || progressKey === lastKey.current) return
    lastKey.current = progressKey
    reload(true)
  }, [progressKey, reload])

  return { data, loading, error, reload }
}
