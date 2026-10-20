import { useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { AlertTriangle, Loader2, Lock, User as UserIcon } from 'lucide-react'
import { login, register } from '@/api/client'
import { ApiError, describeError } from '@/api/http'
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

  const input =
    'w-full rounded-xl border border-slate-200 bg-slate-50/50 py-3 pl-10 pr-3.5 text-[13.5px] font-semibold text-slate-800 outline-none transition-all placeholder:font-medium placeholder:text-slate-300 focus:border-brand-400 focus:bg-white focus:ring-4 focus:ring-brand-500/10'

  return (
    <div className="flex min-h-screen">
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
        </motion.div>
      </div>
    </div>
  )
}
