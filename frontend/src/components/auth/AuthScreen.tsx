import { useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { AlertTriangle, ChevronDown, Loader2, Lock, Server, User as UserIcon } from 'lucide-react'
import { login, register } from '@/api/client'
import { API_BASE, ApiError, describeError, resetApiBase, saveApiBase } from '@/api/http'
import { cn } from '@/utils/cn'
import type { User } from '@/types'

type Mode = 'login' | 'register'

export default function AuthScreen({
  onAuthed,
  initialError,
}: {
  onAuthed: (u: User) => void
  initialError?: string | null
}) {
  const [mode, setMode] = useState<Mode>('login')
  const [loginName, setLoginName] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(initialError ?? null)
  const [showServer, setShowServer] = useState(Boolean(initialError))
  const [serverUrl, setServerUrl] = useState(API_BASE)

  const switchMode = (m: Mode) => {
    setMode(m)
    setError(null)
  }

  const validate = (): string | null => {
    if (mode === 'register') {
      if (loginName.trim().length < 3 || loginName.trim().length > 64)
        return 'Логин должен быть от 3 до 64 символов'
      if (/\s/.test(loginName)) return 'Логин не должен содержать пробелов'
      if (password.length < 8) return 'Пароль должен быть не короче 8 символов'
    }
    return null
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    const v = validate()
    if (v) return setError(v)
    setBusy(true)
    setError(null)
    try {
      if (mode === 'register') {
        await register(loginName.trim(), password, displayName.trim())
      }
      const user = await login(loginName.trim(), password)
      onAuthed(user)
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) setError('Неверный логин или пароль')
      else if (err instanceof ApiError && err.status === 409) setError('Этот логин уже занят')
      else setError(describeError(err))
    } finally {
      setBusy(false)
    }
  }

  const saveServer = () => {
    if (serverUrl.trim()) saveApiBase(serverUrl)
    else resetApiBase()
    window.location.reload()
  }

  const input =
    'w-full rounded-xl border border-slate-200 bg-slate-50/50 py-3 pl-10 pr-3.5 text-[13.5px] font-semibold text-slate-800 outline-none transition-all placeholder:font-medium placeholder:text-slate-300 focus:border-brand-400 focus:bg-white focus:ring-4 focus:ring-brand-500/10'

  return (
    <div className="flex min-h-screen">
      {/* Бренд-панель */}
      {/* 
      <div className="relative hidden w-[46%] overflow-hidden bg-gradient-to-br from-brand-600 via-brand-700 to-[#14296b] lg:block">
        <div className="absolute -left-20 -top-20 h-96 w-96 rounded-full bg-white/10 blur-3xl" />
        <div className="absolute -bottom-24 right-0 h-96 w-96 rounded-full bg-indigo-400/30 blur-3xl" />
        <div className="relative flex h-full flex-col justify-between p-12 text-white">
          <div className="flex items-center gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-white/15 ring-1 ring-white/25 backdrop-blur">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
                <path d="M4 18V7l5.5 7L12 9.5 14.5 14 20 6v12" stroke="white" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </span>
            <span className="text-[19px] font-extrabold tracking-tight">MarketAnalytics</span>
          </div>

          <div>
            <h2 className="text-[34px] font-extrabold leading-[1.15] tracking-tight">
              Доходы, расходы и прибыль
              <br />
              вашего магазина — по дням
            </h2>
            <p className="mt-4 max-w-[400px] text-[14px] leading-relaxed text-white/70">
              Подключите кабинет Ozon по API-ключу и смотрите динамику финансов, структуру расходов и
              продажи по каждому товару.
            </p>
            <div className="mt-8 flex gap-3">
              {['Динамика по дням', 'Сверка с кабинетом', 'Аналитика по SKU'].map((t) => (
                <span key={t} className="rounded-full bg-white/10 px-3.5 py-1.5 text-[12px] font-semibold ring-1 ring-white/15">
                  {t}
                </span>
              ))}
            </div>
          </div>

          <div className="text-[12px] font-medium text-white/40">Данные только на чтение</div>
        </div>
      </div>*/}

      {/* Форма */}
      <div className="flex flex-1 items-center justify-center p-6">
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
          className="w-full max-w-[400px]"
        >
          <div className="mb-8 flex items-center gap-2.5 lg:hidden">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-brand-500 to-brand-700">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M4 18V7l5.5 7L12 9.5 14.5 14 20 6v12" stroke="white" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </span>
            <span className="text-[16px] font-extrabold tracking-tight text-slate-900">
              Market<span className="text-brand-600">Analytics</span>
            </span>
          </div>

          <h1 className="text-[26px] font-extrabold tracking-tight text-slate-900">
            {mode === 'login' ? 'Вход в аккаунт' : 'Создать аккаунт'}
          </h1>
          {/** 
          <p className="mt-1.5 text-[13.5px] font-medium text-slate-500">
            {mode === 'login'
              ? 'Войдите, чтобы увидеть аналитику вашего магазина'
              : 'Регистрация занимает меньше минуты'}
          </p>*/}

          <div className="mt-6 flex rounded-xl bg-slate-100 p-1">
            {(['login', 'register'] as const).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => switchMode(m)}
                className="relative flex-1 rounded-lg py-2 text-[13px] font-bold"
              >
                {mode === m && (
                  <motion.span
                    layoutId="auth-tab"
                    className="absolute inset-0 rounded-lg bg-white shadow-sm ring-1 ring-slate-200/70"
                    transition={{ type: 'spring', stiffness: 420, damping: 34 }}
                  />
                )}
                <span className={cn('relative z-10', mode === m ? 'text-slate-900' : 'text-slate-500')}>
                  {m === 'login' ? 'Вход' : 'Регистрация'}
                </span>
              </button>
            ))}
          </div>

          <form onSubmit={submit} className="mt-5 space-y-3.5">
            <div className="relative">
              <UserIcon size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-300" />
              <input
                value={loginName}
                onChange={(e) => setLoginName(e.target.value)}
                placeholder="Логин"
                autoComplete="username"
                className={input}
              />
            </div>

            <AnimatePresence initial={false}>
              {mode === 'register' && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  className="overflow-hidden"
                >
                  <div className="relative">
                    <UserIcon size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-300" />
                    <input
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      placeholder="Имя для отображения (необязательно)"
                      className={input}
                    />
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            <div className="relative">
              <Lock size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-300" />
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={mode === 'register' ? 'Пароль (минимум 8 символов)' : 'Пароль'}
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                className={input}
              />
            </div>

            {error && (
              <motion.div
                initial={{ opacity: 0, y: -4 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex items-start gap-2.5 rounded-xl bg-rose-50 px-3.5 py-3 text-[12.5px] font-semibold leading-relaxed text-rose-600"
              >
                <AlertTriangle size={15} className="mt-0.5 shrink-0" />
                {error}
              </motion.div>
            )}

            <button
              type="submit"
              disabled={busy || !loginName.trim() || !password}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-brand-600 py-3.5 text-[14px] font-bold text-white shadow-lg shadow-brand-600/30 transition-all hover:bg-brand-700 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {busy && <Loader2 size={16} className="animate-spin" />}
              {mode === 'login' ? 'Войти' : 'Зарегистрироваться'}
            </button>
          </form>

          {/* Настройки сервера */}
          <div className="mt-8 border-t border-slate-200/70 pt-4">
            <button
              onClick={() => setShowServer(!showServer)}
              className="flex items-center gap-2 text-[12px] font-bold text-slate-400 transition-colors hover:text-slate-600"
            >
              <Server size={13} />
              Настройки сервера
              <ChevronDown size={13} className={cn('transition-transform', showServer && 'rotate-180')} />
            </button>
            <AnimatePresence initial={false}>
              {showServer && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  className="overflow-hidden"
                >
                  <div className="pt-3">
                    <label className="mb-1.5 block text-[11px] font-bold text-slate-400">
                      Адрес API (пусто — тот же origin, что у этой страницы)
                    </label>
                    <div className="flex gap-2">
                      <input
                        value={serverUrl}
                        onChange={(e) => setServerUrl(e.target.value)}
                        placeholder="http://localhost:8080"
                        className="min-w-0 flex-1 rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-[12.5px] font-semibold text-slate-700 outline-none focus:border-brand-400 focus:ring-4 focus:ring-brand-500/10"
                      />
                      <button
                        onClick={saveServer}
                        className="rounded-xl bg-slate-800 px-4 text-[12px] font-bold text-white transition-colors hover:bg-slate-900"
                      >
                        Сохранить
                      </button>
                    </div>
                    <p className="mt-2 text-[11px] leading-relaxed text-slate-400">
                      Если фронтенд на другом порту, задайте на бэкенде{' '}
                      <code className="rounded bg-slate-100 px-1 py-0.5 font-semibold text-slate-500">
                        APP_ALLOWED_ORIGINS={window.location.origin}
                      </code>
                    </p>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </motion.div>
      </div>
    </div>
  )
}
