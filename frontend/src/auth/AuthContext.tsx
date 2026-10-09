import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, isUnauthorized, setUnauthorizedListener } from '../api/client'
import type { AuthUser, LoginRequest, RegisterRequest } from '../api/types'
import { clearResumeDraft } from '../lib/resume/draft'

type AuthContextValue = {
  user: AuthUser | null
  /** False until the first GET /api/auth/me has settled. */
  ready: boolean
  /** Why the session could not be checked (the server is unreachable), or null. The user may still be signed in. */
  sessionError: string | null
  retrySession: () => void
  login: (body: LoginRequest) => Promise<void>
  register: (body: RegisterRequest) => Promise<void>
  logout: () => Promise<void>
  /** Keep the header in sync after the profile changes the name or email. */
  updateUser: (patch: Partial<Pick<AuthUser, 'displayName' | 'email'>>) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const [user, setUser] = useState<AuthUser | null>(null)
  const [ready, setReady] = useState(false)
  const [sessionError, setSessionError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

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
        setSessionError(isUnauthorized(err) ? null : err instanceof Error ? err.message : 'Could not check your session')
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
      setUser(null)
      const path = `${window.location.pathname}${window.location.search}`
      if (!path.startsWith('/login') && !path.startsWith('/register')) {
        navigate('/login', { replace: true, state: { from: path } })
      }
    })
    return () => setUnauthorizedListener(null)
  }, [navigate])

  // Signing in proves the server is reachable: a failed check at page load must not outlive it.
  async function login(body: LoginRequest) {
    setUser(await api.login(body))
    setSessionError(null)
  }

  async function register(body: RegisterRequest) {
    setUser(await api.register(body))
    setSessionError(null)
  }

  async function logout() {
    let signedOutByUser = true
    try {
      await api.logout()
    } catch (err) {
      if (!isUnauthorized(err)) throw err // the server session may still be alive
      signedOutByUser = false // the session had expired already
      try {
        await api.refreshCsrf() // already signed out: mint a token for the next sign-in
      } catch {
        // the session is already gone; the next sign-in asks for a token again
      }
    }
    // A sign-out the user asked for removes the CV draft this browser holds for the account: on a shared computer
    // it would otherwise stay readable. An expired session keeps it, so signing back in restores the edits.
    if (signedOutByUser && user) clearResumeDraft(user.id)
    setUser(null)
  }

  const updateUser: AuthContextValue['updateUser'] = (patch) => {
    setUser((current) => (current ? { ...current, ...patch } : current))
  }

  return (
    <AuthContext.Provider value={{ user, ready, sessionError, retrySession, login, register, logout, updateUser }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used within AuthProvider')
  return value
}
