import { errorMessage } from '../lib/abort'
import { forgetAll } from '../lib/pageCache'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, isUnauthorized, setUnauthorizedListener } from '../api/client'
import type { AuthUser, LoginRequest } from '../api/types'
import { clearAllResumeDrafts } from '../lib/resume/draft'

type AuthContextValue = {
  user: AuthUser | null
  /** False until the first GET /api/auth/me has settled. */
  ready: boolean
  /** Why the session could not be checked (the server is unreachable), or null. The user may still be signed in. */
  sessionError: string | null
  retrySession: () => void
  login: (body: LoginRequest) => Promise<void>
  logout: () => Promise<void>
  /** Keep the header in sync after the profile changes the name or email. */
  updateUser: (patch: Partial<Pick<AuthUser, 'displayName' | 'email'>>) => void
}

/** Pages that work signed out: a 401 there is no reason to send anyone to sign-in. */
const PUBLIC_PATHS = ['/login', '/register', '/forgot-password', '/verify-email', '/reset-password']

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const [user, setUser] = useState<AuthUser | null>(null)
  const [ready, setReady] = useState(false)
  const [sessionError, setSessionError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  const userId = user?.id
  useEffect(() => forgetAll(), [userId])

  useEffect(() => {
    let cancelled = false
    api
      .getMe()
      .then((me) => {
        if (cancelled) return
        setUser(me)
        setSessionError(null)
      })
      .catch((err) => {
        if (cancelled) return
        // Only a 401 means "not signed in". A dropped network or a restarting server says nothing about the
        // session, and treating it as a sign-out would send a signed-in user to the login page.
        setUser(null)
        // The session ended while the page was closed: the draft left in this browser goes with it.
        if (isUnauthorized(err)) clearAllResumeDrafts()
        setSessionError(isUnauthorized(err) ? null : errorMessage(err, 'Could not check your session'))
      })
      .finally(() => !cancelled && setReady(true))
    return () => {
      cancelled = true
    }
  }, [attempt])

  const retrySession = useCallback(() => {
    setReady(false)
    setAttempt((n) => n + 1)
  }, [])

  // A 401 on a protected call means the session expired: drop it and go to sign-in.
  useEffect(() => {
    setUnauthorizedListener(() => {
      clearAllResumeDrafts()
      setUser(null)
      const path = `${window.location.pathname}${window.location.search}`
      if (!PUBLIC_PATHS.some((prefix) => path.startsWith(prefix))) {
        navigate('/login', { replace: true, state: { from: path } })
      }
    })
    return () => setUnauthorizedListener(null)
  }, [navigate])

  // Signing in proves the server is reachable: a failed check at page load must not outlive it.
  const login = useCallback(async (body: LoginRequest) => {
    setUser(await api.login(body))
    setSessionError(null)
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.logout()
    } catch (err) {
      if (!isUnauthorized(err)) throw err // the server session may still be alive
      try {
        await api.refreshCsrf() // already signed out: mint a token for the next sign-in
      } catch {
        // the session is already gone; the next sign-in asks for a token again
      }
    }
    // However the session ended, the CV draft this browser holds goes with it: on a shared computer it would
    // otherwise stay readable.
    clearAllResumeDrafts()
    setUser(null)
  }, [])

  const updateUser = useCallback<AuthContextValue['updateUser']>((patch) => {
    setUser((current) => (current ? { ...current, ...patch } : current))
  }, [])

  const value = useMemo(
    () => ({ user, ready, sessionError, retrySession, login, logout, updateUser }),
    [user, ready, sessionError, retrySession, login, logout, updateUser],
  )

  return (
    <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used within AuthProvider')
  return value
}
