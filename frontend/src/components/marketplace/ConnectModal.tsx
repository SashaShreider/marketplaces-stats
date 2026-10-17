import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { CheckCircle2, Eye, EyeOff, KeyRound, Loader2, Plus, X } from 'lucide-react'
import { createAccount, listMarketplaces, putAccountCredentials } from '@/api/client'
import { describeError } from '@/api/http'
import type { AccountInfo, MarketplaceInfo } from '@/types'
import { MarketplaceLogo } from '@/components/ui'
import { shopLabel } from '@/utils/marketplace'

type Phase = 'form' | 'loading' | 'success'

/**
 * Подключение магазина и замена его ключей.
 *
 * <p>Два разных действия в одном окне, потому что отличаются они одним полем:
 * при подключении платформу выбирают, при замене ключей она уже известна.
 * В первом случае платформа — это тип создаваемого магазина, во втором её не
 * меняют: замена ключей чинит существующий кабинет, а не переносит его на другой.
 *
 * <p>Окно вызывается всегда, даже когда магазинов нет, поэтому его открытие не
 * зависит от выбранного в списке: подключать магазин нужно и тогда, когда их нет.
 */
export default function ConnectModal({
  account,
  onClose,
  onConnected,
}: {
  /** null — подключение нового магазина; иначе замена его ключей */
  account: AccountInfo | null
  onClose: () => void
  onConnected: (account: AccountInfo) => void
}) {
  const [platforms, setPlatforms] = useState<MarketplaceInfo[]>([])
  const [code, setCode] = useState<string>('')
  const [clientId, setClientId] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [showKey, setShowKey] = useState(false)
  const [phase, setPhase] = useState<Phase>('form')
  const [error, setError] = useState<string | null>(null)

  // Подключение требует знать, какие платформы вообще поддерживаются: без этого
  // клиент не отличил бы «нет такого маркетплейса» от «есть, но не подключён».
  useEffect(() => {
    if (account) return
    let cancelled = false
    listMarketplaces()
      .then((l) => {
        if (cancelled) return
        setPlatforms(l)
        setCode((prev) => prev || (l[0]?.code ?? ''))
      })
      .catch((e) => !cancelled && setError(describeError(e)))
    return () => {
      cancelled = true
    }
  }, [account])

  // При открытии подставляем известный clientId (замена ключа)
  useEffect(() => {
    setClientId(account?.clientId ?? '')
    setApiKey('')
    setShowKey(false)
    setPhase('form')
    setError(null)
  }, [account?.id])

  const isOzon = (code || account?.marketplaceCode || '').toUpperCase() === 'OZON'
  const idLabel = isOzon ? 'Client-Id' : 'ID компании'
  const keyLabel = isOzon ? 'API-Key' : 'API-токен'
  const platformName = platforms.find((p) => p.code === code)?.name ?? account?.marketplaceName

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!apiKey.trim() || !clientId.trim()) return
    if (!account && !code) return
    setPhase('loading')
    setError(null)
    try {
      const saved = account
        ? await putAccountCredentials(account.id, clientId.trim(), apiKey.trim())
        : await createAccount(code, clientId.trim(), apiKey.trim())
      setPhase('success')
      setTimeout(() => {
        onConnected(saved)
        onClose()
      }, 900)
    } catch (err) {
      setPhase('form')
      setError(describeError(err))
    }
  }

  const inputCls =
    'w-full rounded-xl border border-slate-200 bg-slate-50/50 px-3.5 py-2.5 text-[13px] font-semibold text-slate-800 outline-none transition-all placeholder:font-medium placeholder:text-slate-300 focus:border-brand-400 focus:bg-white focus:ring-4 focus:ring-brand-500/10'

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-[70] flex items-center justify-center p-4">
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.18 }}
          onClick={phase === 'loading' ? undefined : onClose}
          className="absolute inset-0 bg-slate-900/40 backdrop-blur-sm"
        />
        <motion.div
          initial={{ opacity: 0, scale: 0.94, y: 16 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.96, y: 10 }}
          transition={{ type: 'spring', stiffness: 380, damping: 30 }}
          className="relative max-h-[90vh] w-full max-w-[420px] overflow-y-auto rounded-3xl border border-slate-200/70 bg-white p-6 shadow-2xl shadow-slate-900/20"
        >
          <button
            onClick={onClose}
            className="absolute right-4 top-4 flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
          >
            <X size={16} />
          </button>

          {phase === 'success' ? (
            <div className="flex flex-col items-center py-8 text-center">
              <motion.span
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                transition={{ type: 'spring', stiffness: 300, damping: 16 }}
                className="flex h-16 w-16 items-center justify-center rounded-full bg-emerald-50 text-emerald-500"
              >
                <CheckCircle2 size={34} />
              </motion.span>
              <div className="mt-4 text-[16px] font-extrabold text-slate-900">
                {platformName} подключён
              </div>
              <div className="mt-1 text-[12.5px] font-medium text-slate-500">
                Ключи проверены и сохранены
              </div>
            </div>
          ) : (
            <>
              <div className="flex items-center gap-3">
                <MarketplaceLogo code={account?.marketplaceCode ?? code} size="lg" />
                <div>
                  <div className="text-[16px] font-extrabold tracking-tight text-slate-900">
                    {account ? 'Обновить ключи' : 'Подключить магазин'}
                  </div>
                  <div className="text-[12px] font-medium text-slate-400">
                    {isOzon ? 'Личный кабинет Ozon Seller' : 'Кабинет продавца'}
                  </div>
                </div>
              </div>

              {account && (
                <div className="mt-4 flex items-center gap-2 rounded-xl bg-slate-50 px-3.5 py-2.5">
                  <span className="text-[12px] font-semibold text-slate-400">Магазин:</span>
                  <span className="truncate text-[12.5px] font-bold text-slate-700">
                    {shopLabel(account)}
                  </span>
                  <span className="ml-auto shrink-0 font-mono text-[11.5px] font-semibold text-slate-400">
                    {account.clientId}
                  </span>
                </div>
              )}

              <form onSubmit={submit} className="mt-5 space-y-3.5">
                {!account && (
                  <div>
                    <label className="mb-1.5 block text-[11.5px] font-bold text-slate-500">
                      Маркетплейс
                    </label>
                    <div className="flex flex-wrap gap-1.5">
                      {platforms.map((p) => (
                        <button
                          key={p.code}
                          type="button"
                          onClick={() => setCode(p.code)}
                          className={`flex items-center gap-1.5 rounded-xl border px-3 py-2 text-[12.5px] font-bold transition-all ${
                            p.code === code
                              ? 'border-brand-300 bg-brand-50 text-brand-700 shadow-sm'
                              : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300'
                          }`}
                        >
                          <MarketplaceLogo code={p.code} size="sm" />
                          {p.name}
                          {p.connected && (
                            <span className="text-[10.5px] font-semibold text-emerald-500">
                              есть
                            </span>
                          )}
                        </button>
                      ))}
                    </div>
                    <p className="mt-1.5 text-[11px] leading-relaxed text-slate-400">
                      Один кабинет — один магазин. Если у вас их несколько, подключите каждый отдельно.
                    </p>
                  </div>
                )}

                <div>
                  <label className="mb-1.5 block text-[11.5px] font-bold text-slate-500">{idLabel}</label>
                  <input
                    value={clientId}
                    onChange={(e) => setClientId(e.target.value)}
                    placeholder="Например, 1234"
                    className={inputCls}
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-[11.5px] font-bold text-slate-500">{keyLabel}</label>
                  <div className="relative">
                    <KeyRound size={14} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-300" />
                    <input
                      type={showKey ? 'text' : 'password'}
                      value={apiKey}
                      onChange={(e) => setApiKey(e.target.value)}
                      placeholder="Вставьте ключ доступа"
                      className={`${inputCls} !pl-9 !pr-10`}
                    />
                    <button
                      type="button"
                      onClick={() => setShowKey(!showKey)}
                      className="absolute right-2.5 top-1/2 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-slate-100"
                    >
                      {showKey ? <EyeOff size={14} /> : <Eye size={14} />}
                    </button>
                  </div>
                </div>

                <p className="rounded-xl bg-slate-50 px-3.5 py-2.5 text-[11.5px] leading-relaxed text-slate-500">
                  Перед сохранением ключи проверяются настоящим запросом к маркетплейсу. Секретный ключ
                  не возвращается сервером и не отображается повторно.
                </p>

                {error && (
                  <p className="rounded-xl bg-rose-50 px-3.5 py-2.5 text-[12px] font-semibold leading-relaxed text-rose-600">
                    {error}
                  </p>
                )}

                <button
                  type="submit"
                  disabled={
                    !apiKey.trim() ||
                    !clientId.trim() ||
                    (!account && !code) ||
                    phase === 'loading'
                  }
                  className="flex w-full items-center justify-center gap-2 rounded-xl bg-brand-600 py-3 text-[13.5px] font-bold text-white shadow-lg shadow-brand-600/30 transition-all hover:bg-brand-700 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {phase === 'loading' ? (
                    <>
                      <Loader2 size={16} className="animate-spin" />
                      Проверяем ключ…
                    </>
                  ) : account ? (
                    'Сохранить ключ'
                  ) : (
                    <>
                      <Plus size={15} strokeWidth={3} />
                      Подключить магазин
                    </>
                  )}
                </button>
              </form>
            </>
          )}
        </motion.div>
      </div>
    </AnimatePresence>
  )
}