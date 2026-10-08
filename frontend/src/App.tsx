import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { GuestOnly, RequireAuth } from './auth/RouteGuards'
import { AppLayout, AuthLayout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { ProfilePage } from './pages/ProfilePage'
import { RegisterPage } from './pages/RegisterPage'
import { ResumePage } from './pages/ResumePage'
import { VacanciesPage } from './pages/VacanciesPage'

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
              <Route index element={<Navigate to="/vacancies" replace />} />
              <Route path="vacancies/:id?" element={<VacanciesPage />} />
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
