import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, isUnauthorized, setUnauthorizedListener } from '../api/client'
import type { AuthUser, LoginRequest, RegisterRequest } from '../api/types'

type AuthContextValue = {
  user: AuthUser | null
  /** False until the first GET /api/auth/me has settled. */
  ready: boolean
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

  useEffect(() => {
    let cancelled = false
    api
      .getMe()
      .then((me) => !cancelled && setUser(me))
      .catch(() => !cancelled && setUser(null))
      .finally(() => !cancelled && setReady(true))
    return () => {
      cancelled = true
    }
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

  async function login(body: LoginRequest) {
    setUser(await api.login(body))
  }

  async function register(body: RegisterRequest) {
    setUser(await api.register(body))
  }

  async function logout() {
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
    setUser(null)
  }

  const updateUser: AuthContextValue['updateUser'] = (patch) => {
    setUser((current) => (current ? { ...current, ...patch } : current))
  }

  return (
    <AuthContext.Provider value={{ user, ready, login, register, logout, updateUser }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used within AuthProvider')
  return value
}
