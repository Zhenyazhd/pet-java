import { useEffect, useRef } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Button } from '../components/ui/Button'
import { useAuth } from './AuthContext'

function CheckingSession() {
  return (
    <p className="session-check status-text" role="status">
      Checking session…
    </p>
  )
}

function ServerUnreachable({ message, onRetry }: { message: string; onRetry: () => void }) {
  const heading = useRef<HTMLHeadingElement>(null)
  // Retrying swaps this screen for "Checking session…" and back: the keyboard must land on the new one.
  useEffect(() => heading.current?.focus(), [])

  return (
    <main className="fatal">
      <p className="small-caps">Connection problem</p>
      <h1 ref={heading} tabIndex={-1}>
        We could not reach the server
      </h1>
      <p role="alert">{message}</p>
      <Button variant="outline" onClick={onRetry}>
        Try again
      </Button>
    </main>
  )
}

export function RequireAuth() {
  const { user, ready, sessionError, retrySession } = useAuth()
  const location = useLocation()

  if (!ready) return <CheckingSession />
  // Not "signed out": the check itself failed, and the session may well be alive.
  if (!user && sessionError) return <ServerUnreachable message={sessionError} onRetry={retrySession} />
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
