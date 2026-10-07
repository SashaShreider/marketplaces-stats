// ─────────────────────────────────────────────────────────────────────────────
//  Низкоуровневый HTTP-клиент: сессионная кука, CSRF, ошибки RFC 7807.
//
//  Адрес бэкенда выбирается так (по приоритету):
//   1. значение, сохранённое на экране входа («Настройки сервера») — localStorage
//   2. переменная окружения VITE_API_BASE_URL
//   3. в dev-режиме — http://localhost:8080, в production — тот же origin ('')
//
//  Если фронтенд запущен на другом порту, на бэкенде нужно задать
//  APP_ALLOWED_ORIGINS=http://localhost:5173
// ─────────────────────────────────────────────────────────────────────────────

const LS_KEY = 'ma.apiBaseUrl'

type EnvShape = { env?: Record<string, string | boolean | undefined> }

function resolveBase(): string {
  try {
    const saved = localStorage.getItem(LS_KEY)
    if (saved !== null) return saved
  } catch {
    /* localStorage недоступен */
  }
  const env = (import.meta as unknown as EnvShape).env
  const fromEnv = env?.VITE_API_BASE_URL
  if (typeof fromEnv === 'string' && fromEnv) return fromEnv
  return env?.DEV ? 'http://localhost:8080' : ''
}

export const API_BASE = resolveBase().replace(/\/+$/, '')

export function saveApiBase(value: string) {
  localStorage.setItem(LS_KEY, value.trim())
}

export function resetApiBase() {
  localStorage.removeItem(LS_KEY)
}

// ─── Ошибки ──────────────────────────────────────────────────────────────────

export interface Problem {
  type?: string
  title?: string
  detail?: string
  status?: number
  conflict?: string
  activeImportId?: number
  remoteStatus?: number
  ozonStatus?: number
  ozonCode?: string
  [k: string]: unknown
}

export class ApiError extends Error {
  status: number
  problem: Problem | null
  constructor(status: number, problem: Problem | null, message: string) {
    super(message)
    this.status = status
    this.problem = problem
  }
}

/**TODO - проверить коды ошибок - пишет попробуйте позже, даже когда ключ неверный */
/** Человекочитаемое описание любой ошибки */
export function describeError(e: unknown): string {
  if (e instanceof ApiError) {
    const p = e.problem
    if (e.status === 502) {
      const code = p?.ozonStatus ?? p?.ozonCode
      if (p?.ozonStatus === 401 || p?.ozonStatus === 403)
        return `Маркетплейс отверг API-ключ (${p.ozonStatus}). Обновите ключ в профиле.`
      return `Ошибка API маркетплейса${code ? ` (${code})` : ''}. Попробуйте позже.`
    }
    if (p?.type?.endsWith('credentials-rejected')) {
      return `Маркетплейс отверг ключи${p.remoteStatus ? ` (код ${p.remoteStatus})` : ''}. Проверьте Client-Id и API-ключ.`
    }
    return e.message
  }
  return e instanceof Error ? e.message : 'Неизвестная ошибка'
}

// ─── CSRF ────────────────────────────────────────────────────────────────────

let csrf: { header: string; token: string } | null = null

function url(path: string, query?: Record<string, string | number | null | undefined>): string {
  let u = `${API_BASE}${path}`
  if (query) {
    const sp = new URLSearchParams()
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null && v !== '') sp.set(k, String(v))
    }
    const s = sp.toString()
    if (s) u += `?${s}`
  }
  return u
}

function networkError(): ApiError {
  return new ApiError(
    0,
    null,
    `Нет связи с сервером (${API_BASE || window.location.origin}). Проверьте адрес, что бэкенд запущен и что в APP_ALLOWED_ORIGINS указан адрес этого фронтенда.`,
  )
}

export async function refreshCsrf(): Promise<void> {
  try {
    const res = await fetch(url('/api/auth/csrf'), {
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) throw new ApiError(res.status, null, `Не удалось получить CSRF-токен (${res.status})`)
    const j = (await res.json()) as { headerName: string; token: string }
    csrf = { header: j.headerName || 'X-XSRF-TOKEN', token: j.token }
  } catch (e) {
    if (e instanceof ApiError) throw e
    throw networkError()
  }
}

export function forgetCsrf() {
  csrf = null
}

// ─── Реакция на 401 ──────────────────────────────────────────────────────────

let onUnauthorized: (() => void) | null = null
export function setUnauthorizedHandler(fn: (() => void) | null) {
  onUnauthorized = fn
}

// ─── Запрос ──────────────────────────────────────────────────────────────────

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

interface Options {
  query?: Record<string, string | number | null | undefined>
  body?: unknown
  /** не вызывать глобальный обработчик 401 (для /auth/login, /auth/me) */
  silent401?: boolean
}

export async function request<T>(method: Method, path: string, opts: Options = {}): Promise<T> {
  const mutating = method !== 'GET'
  if (mutating && !csrf) await refreshCsrf()

  const send = () => {
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (opts.body !== undefined) headers['Content-Type'] = 'application/json'
    if (mutating && csrf) headers[csrf.header] = csrf.token
    return fetch(url(path, opts.query), {
      method,
      credentials: 'include',
      headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    })
  }

  let res: Response
  try {
    res = await send()
    // CSRF-токен мог устареть (например, после смены сессии) — обновляем и повторяем один раз
    if (res.status === 403 && mutating) {
      await refreshCsrf()
      res = await send()
    }
  } catch (e) {
    if (e instanceof ApiError) throw e
    throw networkError()
  }

  const text = await res.text()
  let data: unknown = undefined
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = undefined
    }
  }

  if (!res.ok) {
    const problem = (data && typeof data === 'object' ? (data as Problem) : null) ?? null
    if (res.status === 401 && !opts.silent401) onUnauthorized?.()
    const message =
      problem?.detail ||
      problem?.title ||
      (res.status === 401
        ? 'Требуется вход'
        : res.status === 403
          ? 'Доступ запрещён'
          : `Ошибка запроса (${res.status})`)
    throw new ApiError(res.status, problem, message)
  }

  return data as T
}
