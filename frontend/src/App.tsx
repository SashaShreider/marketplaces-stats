import { useCallback, useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { AlertTriangle, Loader2, RefreshCw } from 'lucide-react'
import {
  deleteMarketplace, listMarketplaces, logout as apiLogout, me,
} from './api/client'
import { ApiError, describeError, forgetCsrf, setUnauthorizedHandler } from './api/http'
import AuthScreen from './components/AuthScreen'
import ConnectModal from './components/ConnectModal'
import Sidebar, { type PageId } from './components/Sidebar'
import TopBar from './components/TopBar'
import { useImportRunner } from './hooks/useImportRunner'
import OverviewPage from './pages/OverviewPage'
import ProductsPage from './pages/ProductsPage'
import type { DateRange, MarketplaceInfo, User } from './types'
import { cn } from './utils/cn'
import { lastNDays } from './utils/format'

// ─── Основное приложение (пользователь уже вошёл) ───────────────────────────

function Dashboard({ user, onLogout }: { user: User; onLogout: () => void }) {
  const [list, setList] = useState<MarketplaceInfo[] | null>(null)
  const [listError, setListError] = useState<string | null>(null)
  const [code, setCode] = useState<string | null>(null)
  const [page, setPage] = useState<PageId>('overview')
  const [range, setRange] = useState<DateRange>(() => lastNDays(30))
  const [modal, setModal] = useState<string | null>(null)

  const loadList = useCallback(async () => {
    try {
      const l = await listMarketplaces()
      setList(l)
      setListError(null)
      setCode((prev) =>
        prev && l.some((m) => m.code === prev) ? prev : (l.find((m) => m.connected) ?? l[0])?.code ?? null,
      )
    } catch (e) {
      setListError(describeError(e))
    }
  }, [])

  useEffect(() => {
    loadList()
  }, [loadList])

  const current = list?.find((m) => m.code === code) ?? null

  // один раннер импортов на приложение: прогресс виден на обеих страницах
  const runner = useImportRunner(current?.connected ? current.code : null, () => {
    loadList() // обновятся catalogProducts / importRuns
  })

  const disconnect = async (c: string) => {
    try {
      await deleteMarketplace(c)
    } catch (e) {
      // 409: уже не подключён — просто обновим список
      if (!(e instanceof ApiError && e.status === 409)) throw e
    }
    await loadList()
  }

  const modalMp = list?.find((m) => m.code === modal) ?? null

  return (
    <div className="min-h-screen">
      <div className="pointer-events-none fixed inset-0 -z-10">
        <div className="absolute -top-32 left-1/4 h-96 w-96 rounded-full bg-brand-100/50 blur-3xl" />
        <div className="absolute bottom-0 right-0 h-80 w-80 rounded-full bg-indigo-100/40 blur-3xl" />
      </div>

      <div className="flex">
        <Sidebar page={page} onNavigate={setPage} />

        <div className="min-w-0 flex-1">
          <TopBar
            user={user}
            list={list ?? []}
            current={current}
            onMarketplaceChange={setCode}
            onConnect={setModal}
            onDisconnect={disconnect}
            onLogout={onLogout}
          />

          <main className="mx-auto w-full max-w-[1180px] px-4 pb-24 pt-6 sm:px-6 lg:pb-12">
            {listError ? (
              <div className="flex flex-col items-center rounded-2xl border border-rose-200 bg-rose-50 px-6 py-14 text-center">
                <AlertTriangle size={30} className="text-rose-500" />
                <p className="mt-3 max-w-[460px] text-[13.5px] font-semibold leading-relaxed text-rose-700">
                  {listError}
                </p>
                <button
                  onClick={loadList}
                  className="mt-5 flex items-center gap-2 rounded-xl bg-white px-4 py-2.5 text-[13px] font-bold text-slate-700 shadow-sm ring-1 ring-rose-200 transition-all hover:ring-rose-300"
                >
                  <RefreshCw size={14} />
                  Повторить
                </button>
              </div>
            ) : !list ? (
              <div className="grid gap-4 md:grid-cols-3">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="animate-pulse-soft h-[196px] rounded-2xl bg-white/80 ring-1 ring-slate-200/60" />
                ))}
              </div>
            ) : !current ? (
              <div className="rounded-2xl border border-dashed border-slate-300 bg-white/60 px-6 py-16 text-center text-[13.5px] font-medium text-slate-500">
                Сервер не вернул ни одного маркетплейса.
              </div>
            ) : (
              <AnimatePresence mode="wait" initial={false}>
                <motion.div
                  key={page}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -8 }}
                  transition={{ duration: 0.2 }}
                >
                  {page === 'overview' ? (
                    <OverviewPage
                      key={current.code}
                      mp={current}
                      range={range}
                      onRangeChange={setRange}
                      runner={runner}
                      onConnect={() => setModal(current.code)}
                    />
                  ) : (
                    <ProductsPage
                      key={current.code}
                      mp={current}
                      range={range}
                      onRangeChange={setRange}
                      runner={runner}
                      onConnect={() => setModal(current.code)}
                    />
                  )}
                </motion.div>
              </AnimatePresence>
            )}
          </main>
        </div>
      </div>

      {/* мобильная навигация */}
      <nav className="fixed bottom-4 left-1/2 z-40 flex -translate-x-1/2 gap-1 rounded-full border border-slate-200/80 bg-white/90 p-1.5 shadow-xl shadow-slate-900/10 backdrop-blur-xl lg:hidden">
        {(
          [
            { id: 'overview', label: 'Обзор' },
            { id: 'products', label: 'Товары' },
          ] as { id: PageId; label: string }[]
        ).map((item) => (
          <button
            key={item.id}
            onClick={() => setPage(item.id)}
            className={cn(
              'rounded-full px-5 py-2 text-[12.5px] font-bold transition-colors',
              page === item.id ? 'bg-brand-600 text-white shadow-md shadow-brand-600/30' : 'text-slate-500',
            )}
          >
            {item.label}
          </button>
        ))}
      </nav>

      <ConnectModal
        mp={modalMp}
        onClose={() => setModal(null)}
        onConnected={async (c) => {
          await loadList()
          setCode(c)
        }}
      />
    </div>
  )
}

// ─── Корень: проверка сессии → вход или приложение ──────────────────────────

export default function App() {
  const [auth, setAuth] = useState<'loading' | 'anon' | 'authed'>('loading')
  const [user, setUser] = useState<User | null>(null)
  const [bootError, setBootError] = useState<string | null>(null)

  useEffect(() => {
    // любой 401 от API означает, что сессия закончилась — показываем вход
    setUnauthorizedHandler(() => {
      setUser(null)
      setAuth('anon')
    })
    me()
      .then((u) => {
        setUser(u)
        setAuth('authed')
      })
      .catch((e) => {
        // 401 — штатная ситуация «не вошли»; остальное (нет связи, CORS) — показываем причину
        if (!(e instanceof ApiError && e.status === 401)) setBootError(describeError(e))
        setAuth('anon')
      })
    return () => setUnauthorizedHandler(null)
  }, [])

  const handleLogout = async () => {
    try {
      await apiLogout()
    } catch {
      /* сессия в любом случае сбрасывается на клиенте */
    }
    forgetCsrf()
    setUser(null)
    setAuth('anon')
  }

  if (auth === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Loader2 size={28} className="animate-spin text-brand-600" />
      </div>
    )
  }

  if (auth === 'anon' || !user) {
    return (
      <AuthScreen
        initialError={bootError}
        onAuthed={(u) => {
          setBootError(null)
          setUser(u)
          setAuth('authed')
        }}
      />
    )
  }

  return <Dashboard user={user} onLogout={handleLogout} />
}
