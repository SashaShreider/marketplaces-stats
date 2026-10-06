import { useCallback, useEffect, useRef, useState } from 'react'
import { getImport, listImports, startCatalogImport, startFinanceImport } from '../api/client'
import { ApiError, describeError } from '../api/http'
import type { ImportProgress } from '../types'

export interface ImportRunner {
  /** Последний известный прогон (идущий или только что завершённый) */
  run: ImportProgress | null
  /** Ошибка запуска импорта */
  error: string | null
  busy: boolean
  startFinance: (dateFrom: string, dateTo: string) => Promise<void>
  startCatalog: () => Promise<void>
  dismiss: () => void
}

const POLL_MS = 1500

/**
 * Запускает фоновые импорты и опрашивает GET /imports/{id}, пока inProgress = true.
 * При открытии подхватывает уже идущий импорт (например, после перезагрузки страницы).
 */
export function useImportRunner(
  mp: string | null,
  onFinished?: (p: ImportProgress) => void,
): ImportRunner {
  const [run, setRun] = useState<ImportProgress | null>(null)
  const [error, setError] = useState<string | null>(null)
  const finishedRef = useRef(onFinished)
  finishedRef.current = onFinished
  const notifiedRef = useRef<number | null>(null)

  // подхватываем идущий импорт
  useEffect(() => {
    setRun(null)
    setError(null)
    if (!mp) return
    let cancelled = false
    listImports(mp)
      .then((list) => {
        if (cancelled) return
        const active = list.find((i) => i.inProgress)
        if (active) setRun(active)
      })
      .catch(() => {
        /* список — не критичен */
      })
    return () => {
      cancelled = true
    }
  }, [mp])

  // опрос прогресса
  const runId = run?.id
  const inProgress = run?.inProgress ?? false
  useEffect(() => {
    if (!mp || runId === undefined || !inProgress) return
    const timer = window.setInterval(async () => {
      try {
        const p = await getImport(mp, runId)
        setRun(p)
        if (!p.inProgress && notifiedRef.current !== p.id) {
          notifiedRef.current = p.id
          finishedRef.current?.(p)
        }
      } catch {
        /* временный сбой сети — попробуем на следующем тике */
      }
    }, POLL_MS)
    return () => window.clearInterval(timer)
  }, [mp, runId, inProgress])

  const start = useCallback(
    async (fn: () => Promise<ImportProgress>) => {
      if (!mp) return
      setError(null)
      try {
        const p = await fn()
        notifiedRef.current = null
        setRun(p)
      } catch (e) {
        // 409: такой импорт уже идёт — просто присоединяемся к нему
        if (e instanceof ApiError && e.status === 409 && e.problem?.activeImportId) {
          try {
            const p = await getImport(mp, e.problem.activeImportId)
            notifiedRef.current = null
            setRun(p)
            return
          } catch {
            /* упадём в общее сообщение */
          }
        }
        setError(describeError(e))
      }
    },
    [mp],
  )

  const startFinance = useCallback(
    (from: string, to: string) => start(() => startFinanceImport(mp!, from, to)),
    [mp, start],
  )
  const startCatalog = useCallback(() => start(() => startCatalogImport(mp!)), [mp, start])

  const dismiss = useCallback(() => {
    setError(null)
    setRun((r) => (r && !r.inProgress ? null : r))
  }, [])

  return { run, error, busy: inProgress, startFinance, startCatalog, dismiss }
}
