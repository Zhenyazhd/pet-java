import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { GuestOnly, RequireAuth } from './auth/RouteGuards'
import { AppLayout, AuthLayout } from './components/Layout'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { LoginPage } from './pages/LoginPage'
import { JobMatchesPage, JobsLayout, ProfilePage, ResumePage, SearchProfilePage, VacanciesPage } from './pages/lazy'
import { RegisterPage } from './pages/RegisterPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { VerifyEmailPage } from './pages/VerifyEmailPage'

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
              <Route path="forgot-password" element={<ForgotPasswordPage />} />
            </Route>
          </Route>
          {/* Opened from a mail: whoever is signed in, the link still has to work. */}
          <Route element={<AuthLayout />}>
            <Route path="verify-email" element={<VerifyEmailPage />} />
            <Route path="reset-password" element={<ResetPasswordPage />} />
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
