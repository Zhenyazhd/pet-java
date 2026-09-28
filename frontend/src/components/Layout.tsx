import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function Layout() {
  const { user, logout } = useAuth()

  async function onLogout() {
    try {
      await logout()
    } catch {
      // session already cleared in auth context
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
          <button type="button" className="button button--ghost topbar__logout" onClick={() => void onLogout()}>
            Log out
          </button>
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
