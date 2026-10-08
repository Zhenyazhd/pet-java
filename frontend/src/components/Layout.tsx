import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ErrorBoundary } from './ErrorBoundary'
import { Banner } from './ui/Banner'
import { Button } from './ui/Button'

function Wordmark() {
  return (
    <Link to="/" className="wordmark" aria-label="Job Search — home">
      Job Search
    </Link>
  )
}

export function AppLayout() {
  const { user, logout } = useAuth()
  const location = useLocation()
  const [signingOut, setSigningOut] = useState(false)
  const [signOutError, setSignOutError] = useState<string | null>(null)

  async function onSignOut() {
    if (signingOut) return
    setSigningOut(true)
    setSignOutError(null)
    try {
      await logout()
    } catch (err) {
      setSignOutError(err instanceof Error ? err.message : 'Sign-out failed. Try again.')
    } finally {
      setSigningOut(false)
    }
  }

  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="topbar">
        <div className="topbar__inner">
          <Wordmark />
          <nav className="topbar__nav" aria-label="Main">
            <NavLink to="/vacancies" className="nav-link">
              Vacancies
            </NavLink>
            <NavLink to="/resume" className="nav-link">
              Resume
            </NavLink>
            <NavLink to="/profile" className="nav-link">
              Profile
            </NavLink>
          </nav>
          <div className="topbar__account">
            <span className="topbar__user small-caps">{user?.displayName || user?.email}</span>
            <Button variant="ghost" onClick={onSignOut} aria-disabled={signingOut}>
              {signingOut ? 'Signing out…' : 'Sign out'}
            </Button>
          </div>
        </div>
      </header>
      <main id="main" className="main">
        {signOutError && <Banner tone="error">{signOutError}</Banner>}
        {/* Per page, so a render error keeps the header; the key resets it on navigation. */}
        <ErrorBoundary key={location.pathname} inline>
          <Outlet />
        </ErrorBoundary>
      </main>
    </div>
  )
}

export function AuthLayout() {
  return (
    <div className="auth">
      <aside className="auth__aside">
        <Wordmark />
        <div className="auth__aside-body">
          <p className="small-caps">An invitation-only workspace</p>
          <p className="auth__quote">
            Tell your story once, in full. Everything else is drafted from it.
          </p>
        </div>
        <span className="auth__rule" aria-hidden="true" />
      </aside>
      <main className="auth__main">
        <Outlet />
      </main>
    </div>
  )
}
