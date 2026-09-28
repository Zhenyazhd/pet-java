import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

/** Protects nested routes — redirects anonymous users to /login. */
export function RequireAuth() {
  const { user, ready } = useAuth()
  const location = useLocation()

  if (!ready) {
    return (
      <section className="page">
        <p className="muted">Checking session…</p>
      </section>
    )
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }

  return <Outlet />
}

/** Login/Register only — send authenticated users into the app. */
export function GuestOnly() {
  const { user, ready } = useAuth()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from || '/'

  if (!ready) {
    return (
      <section className="page">
        <p className="muted">Checking session…</p>
      </section>
    )
  }

  if (user) {
    return <Navigate to={from} replace />
  }

  return <Outlet />
}
