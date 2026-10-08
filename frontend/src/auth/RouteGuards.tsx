import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

function CheckingSession() {
  return (
    <p className="session-check status-text" role="status">
      Checking session…
    </p>
  )
}

export function RequireAuth() {
  const { user, ready } = useAuth()
  const location = useLocation()

  if (!ready) return <CheckingSession />
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return <Outlet />
}

/** Signed-in users are sent back to where they came from; this is the only redirect after sign-in. */
export function GuestOnly({ fallback = '/' }: { fallback?: string }) {
  const { user, ready } = useAuth()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from || fallback

  if (!ready) return <CheckingSession />
  if (user) return <Navigate to={from} replace />
  return <Outlet />
}
