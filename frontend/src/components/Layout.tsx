import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ErrorBoundary } from './ErrorBoundary'
import { Button } from './ui/Button'

export function Layout() {
  const { user, logout } = useAuth()
  const location = useLocation()

  async function onLogout() {
    try {
      await logout()
    } catch {
      // Keep UI logged-in if the server session could not be invalidated.
      window.alert('Could not log out. Check your connection and try again.')
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link to="/" className="brand">
          Job Search
        </Link>
        <nav className="nav" aria-label="Primary">
          <NavLink to="/" end>
            Resume
          </NavLink>
          <NavLink to="/vacancies">Vacancies</NavLink>
          <NavLink to="/profile">Profile</NavLink>
        </nav>
        <div className="topbar__account">
          {user && <span className="topbar__user">{user.displayName || user.email}</span>}
          <Button variant="ghost" className="topbar__logout" onClick={() => void onLogout()}>
            Log out
          </Button>
        </div>
      </header>
      <main className="main">
        {/* Keyed by route so navigating away from a crashed page clears the error automatically. */}
        <ErrorBoundary key={location.pathname} hint="This page hit an unexpected error — try another page from the nav above.">
          <Outlet />
        </ErrorBoundary>
      </main>
    </div>
  )
}

/** Minimal shell for login / register. */
export function AuthLayout() {
  const location = useLocation()
  return (
    <div className="app-shell auth-shell">
      <header className="topbar">
        <Link to="/login" className="brand">
          Job Search
        </Link>
      </header>
      <main className="main">
        <ErrorBoundary key={location.pathname}>
          <Outlet />
        </ErrorBoundary>
      </main>
    </div>
  )
}
