import { useCallback, useEffect, useRef, useState } from 'react'
import type { DailyReport, DateRange } from '@/types'
import { addDaysISO, todayISO } from '@/utils/format'
import type { ImportRunner } from '@/features/imports/useImportRunner'
import { syncStateOf, type SyncState } from '@/components/sync/SyncIndicator'

/**
 * Автоматическая загрузка данных.
 *
 * <p>Пользователю не должно быть рутины: пустую базу заполняем сами, недогруженные
 * дни догоняем сами, свежие уточняем при возврате на страницу.
 *
 * <p>Всё это возможно на фронте, потому что сервер идемпотентен: `daysToSync`
 * возвращает только незагруженные, упавшие и неокончательные дни. Повторный запуск
 * за период не тратит квоту зря, а если загружать нечего, прогон заканчивается сразу,
 * не потратив ни одного запроса.
 *
 * <p>Порядок обязателен: пересекающиеся импорты сервер отклоняет с 409. Поэтому
 * догрузка предыдущего периода начинается лишь после того, как отработал текущий.
 */

/** Сколько дней предыдущего периода догружаем после первых 30. */
const DEEP_SYNC_DAYS = 90

/** Как часто можно молча уточнять свежие дни: не чаще раза в сутки. */
const REFRESH_INTERVAL_MS = 24 * 60 * 60 * 1000

/** Пауза между попытками догнать один и тот же период, чтобы не крутить цикл. */
const CATCH_UP_RETRY_MS = 60 * 1000

const PHASE_KEY = 'ma.autoSyncPhase'
const LAST_REFRESH_KEY = 'ma.lastAutoRefresh'
const LAST_CATCH_UP_KEY = 'ma.lastCatchUp'

type Phase = 'fresh' | 'initial' | 'deep' | 'settled'

export interface AutoSync {
  /** Что показывать рядом с календарём */
  state: SyncState | null
}

function read(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function write(key: string, value: string) {
  try {
    localStorage.setItem(key, value)
  } catch {
    /* приватный режим браузера — просто не запомним */
  }
}

function readLast(key: string): number {
  const v = Number(read(key))
  return Number.isFinite(v) && v > 0 ? v : 0
}

/**
 * Период, который догружаем после первых 30 дней.
 *
 * <p>Именно предыдущие 90 дней, а не последние: первые 30 дней уже загружены, и
 * повторный запрос по ним только съел бы квоту. Всё вместе это даёт историю за 120
 * дней, а не за 30.
 */
function deepSyncRange(): DateRange {
  const to = addDaysISO(todayISO(), -30)
  return { from: addDaysISO(to, -(DEEP_SYNC_DAYS - 1)), to }
}

export function useAutoSync({
  mp,
  report,
  range,
  runner,
}: {
  mp: string | null
  report: DailyReport | null
  range: DateRange
  runner: ImportRunner
}): AutoSync {
  const [phase, setPhaseState] = useState<Phase>(() => {
    const v = read(PHASE_KEY)
    return v === 'initial' || v === 'deep' || v === 'settled' ? v : 'fresh'
  })

  // Фаза живёт и в памяти, и в localStorage: перезагрузка страницы не должна
  // заново начинать автозагрузку с первого шага.
  const setPhase = useCallback((next: Phase) => {
    setPhaseState(next)
    write(PHASE_KEY, next)
  }, [])

  // Прогон, который уже обработан. Без него один и тот же завершившийся прогон
  // раз за каждым рендером запускал бы следующий этап.
  const handledRunId = useRef<number | null>(null)
  const refreshing = useRef(false)
  const launching = useRef(false)
  const busyRef = useRef(runner.busy)
  busyRef.current = runner.busy
  const busy = runner.busy
  const run = runner.run

  /**
   * Запуск импорта с защитой от параллельных прогонов.
   *
   * <p>Флаг снимается сразу, а не после конца импорта: `startFinance` сам дожидается
   * завершения прогона, и держать блокировку до этого момента нельзя — тогда бы
   * заблокировался догон и следующий этап.
   */
  const launch = useCallback(
    (from: string, to: string) => {
      if (launching.current || busyRef.current) return false
      launching.current = true
      try {
        // Ошибку запуска раннер показывает сам через runner.error.
        void runner.startFinance(from, to).catch(() => {})
        return true
      } finally {
        launching.current = false
      }
    },
    [runner],
  )

  /**
   * Запуск с паузой между попытками для одного и того же периода.
   *
   * <p>Пауза нужна, чтобы не крутить цикл: если день не загрузился по причине,
   * которую повтор не исправит, бесконечные попытки каждые пару секунд съедали бы
   * квоту и дёргали интерфейс.
   *
   * <p>Отметка ведётся по самому периоду, а не одна на всё приложение. Иначе,
   * догнав один диапазон, мы заблокировали бы догон любого следующего: пользователь
   * выбрал другой период, а импорт не начинался в течение всей паузы.
   */
  const launchThrottled = useCallback(
    (from: string, to: string) => {
      const key = `${LAST_CATCH_UP_KEY}:${from}:${to}`
      const last = readLast(key)
      if (last > 0 && Date.now() - last < CATCH_UP_RETRY_MS) return false
      if (!launch(from, to)) return false
      write(key, String(Date.now()))
      return true
    },
    [launch],
  )

  // Смена маркетплейса возвращает автоматику к началу: у другого магазина своя база.
  const lastMp = useRef(mp)

  /**
   * Догон недогруженных дней выбранного периода.
   *
   * <p>Ключевое условие здесь — `missingDays.length`, а не статус отчёта. Статус
   * `NOT_LOADED` это частный случай того же самого (сервер отдаёт все даты как
   * недостающие), но опираться только на него нельзя: фаза автозагрузки к тому
   * моменту уже не «свежая», и новый период не запускал бы ничего. Один день из
   * диапазона — тоже повод догрузить, поэтому проверяем именно список недостающих
   * дней, а не готовность периода целиком.
   */
  useEffect(() => {
    if (!mp || !report || busy) return

    const missing = report.coverage.missingDays.length
    const failed = report.coverage.failedDays
    if (missing === 0 && failed === 0) return

    if (launchThrottled(range.from, range.to)) {
      // Догон идёт на выбранный период: это уже не первый заход, но фаза должна
      // перейти дальше, иначе догрузка предыдущих 90 дней не запустится никогда.
      if (phase === 'fresh') setPhase('initial')
    }
    // `busy` в зависимостях обязателен: догон должен повториться, когда
    // предыдущий импорт закончился и в базе всё ещё есть недостающие дни.
  }, [mp, report, busy, range.from, range.to, phase, launchThrottled, setPhase])

  // Текущий период закрыт — догружаем предыдущие 90 дней.
  useEffect(() => {
    if (!mp || !report || busy) return
    if (phase !== 'initial') return
    // Ждём именно полной загрузки: при ошибках сначала разбираемся с ними.
    if (report.coverage.needsSync || report.coverage.failedDays > 0) return

    const deep = deepSyncRange()
    if (deep.from >= range.from) {
      // Выбранный период начинается раньше догрузки — идти назад незачем.
      setPhase('settled')
      return
    }
    setPhase('deep')
    launch(deep.from, deep.to)
    // `busy` в зависимостях обязателен: без него эффект не перезапустится в момент,
    // когда импорт текущего периода завершится и снял блокировку.
  }, [mp, report, busy, phase, range.from, launch, setPhase])

  // Прогон завершился: переходим дальше, если это была догрузка.
  useEffect(() => {
    if (!run || run.inProgress) return
    if (handledRunId.current === run.id) return
    handledRunId.current = run.id

    if (phase === 'deep') setPhase('settled')
    refreshing.current = false
  }, [run, phase, setPhase])

  // Тихое уточнение свежих дней при возврате на страницу.
  useEffect(() => {
    if (!mp || !report || busy || refreshing.current) return
    if (report.coverage.provisionalDays.length === 0) return
    // При ошибках сначала разбираются с ними, а не с уточнениями.
    if (report.status === 'HAS_ERRORS' || report.coverage.failedDays > 0) return

    const last = readLast(LAST_REFRESH_KEY)
    if (last > 0 && Date.now() - last < REFRESH_INTERVAL_MS) return

    refreshing.current = true
    // Метку времени ставим только после успешного старта: если импорт не запустился,
    // не помечаем попытку выполненной, иначе уточнение замолчало бы на сутки.
    if (launch(range.from, range.to)) write(LAST_REFRESH_KEY, String(Date.now()))
    else refreshing.current = false
  }, [mp, report, busy, range.from, range.to, launch])

  // Запуск мог быть отвергнут сервером: тогда `run` так и не появится, а снять
  // отметку «идёт обновление» больше некому.
  const runnerError = runner.error
  useEffect(() => {
    if (!runnerError) return
    refreshing.current = false
  }, [runnerError])

  useEffect(() => {
    if (lastMp.current === mp) return
    lastMp.current = mp
    write(PHASE_KEY, 'fresh')
    setPhase('fresh')
    handledRunId.current = null
    refreshing.current = false
  }, [mp, setPhase])

  return { state: syncStateOf(report, run) }
}
