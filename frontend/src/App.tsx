import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { GuestOnly, RequireAuth } from './auth/RouteGuards'
import { AppLayout, AuthLayout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { JobMatchesPage, JobsLayout, ProfilePage, ResumePage, SearchProfilePage, VacanciesPage } from './pages/lazy'
import { RegisterPage } from './pages/RegisterPage'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<GuestOnly />}>
            <Route element={<AuthLayout />}>
              <Route path="login" element={<LoginPage />} />
            </Route>
          </Route>
          <Route element={<GuestOnly fallback="/profile" />}>
            <Route element={<AuthLayout />}>
              <Route path="register" element={<RegisterPage />} />
            </Route>
          </Route>

          <Route element={<RequireAuth />}>
            <Route element={<AppLayout />}>
              <Route index element={<Navigate to="/jobs" replace />} />
              <Route path="vacancies/:id?" element={<VacanciesPage />} />
              <Route path="jobs" element={<JobsLayout />}>
                <Route index element={<JobMatchesPage />} />
                <Route path="profile" element={<SearchProfilePage />} />
              </Route>
              <Route path="resume" element={<ResumePage />} />
              <Route path="profile" element={<ProfilePage />} />
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
