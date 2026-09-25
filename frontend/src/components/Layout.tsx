import { Link, NavLink, Outlet } from 'react-router-dom'

export function Layout() {
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
          <NavLink to="/profile">Profile</NavLink>
        </nav>
      </header>
      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
