import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api, isForbidden, isUnauthorized, setUnauthorizedListener } from '../api/client'
import type { AuthUser, LoginRequest, RegisterRequest } from '../api/types'

type AuthContextValue = {
  user: AuthUser | null
  ready: boolean
  login: (body: LoginRequest) => Promise<void>
  register: (body: RegisterRequest) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [user, setUser] = useState<AuthUser | null>(null)
  const [ready, setReady] = useState(false)

  const clearSession = useCallback(() => {
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const me = await api.getMe()
        if (!cancelled) setUser(me)
      } catch {
        if (!cancelled) setUser(null)
      } finally {
        if (!cancelled) setReady(true)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    setUnauthorizedListener(() => {
      clearSession()
      const path = `${window.location.pathname}${window.location.search}`
      if (!path.startsWith('/login') && !path.startsWith('/register')) {
        navigate('/login', { replace: true, state: { from: path } })
      }
    })
    return () => setUnauthorizedListener(null)
  }, [navigate, clearSession])

  const login = useCallback(
    async (body: LoginRequest) => {
      const next = await api.login(body)
      queryClient.clear()
      setUser(next)
    },
    [queryClient],
  )

  const register = useCallback(
    async (body: RegisterRequest) => {
      const next = await api.register(body)
      queryClient.clear()
      setUser(next)
    },
    [queryClient],
  )

  const logout = useCallback(async () => {
    try {
      await api.logout()
    } catch (err) {
      if (isUnauthorized(err)) {
        // Session already gone on the server — mint CSRF for the next login.
        await api.refreshCsrf()
      } else if (isForbidden(err)) {
        // Stale CSRF: Spring rejected before invalidate. Refresh and retry once.
        await api.refreshCsrf()
        try {
          await api.logout()
        } catch (retryErr) {
          if (!isUnauthorized(retryErr)) throw retryErr
          await api.refreshCsrf()
        }
      } else {
        // Network / 5xx — keep client session so we don't pretend logout succeeded.
        throw err
      }
    }
    // Only clear UI after server logout succeeded or session was already gone (401).
    clearSession()
  }, [clearSession])

  const value = useMemo(
    () => ({ user, ready, login, register, logout }),
    [user, ready, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used within AuthProvider')
  return value
}
