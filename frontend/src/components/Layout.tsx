import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Button } from './ui/Button'

export function Layout() {
  const { user, logout } = useAuth()

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
        <Outlet />
      </main>
    </div>
  )
}

/** Minimal shell for login / register. */
export function AuthLayout() {
  return (
    <div className="app-shell auth-shell">
      <header className="topbar">
        <Link to="/login" className="brand">
          Job Search
        </Link>
      </header>
      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
