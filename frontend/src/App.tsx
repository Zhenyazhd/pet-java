import { lazy } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { GuestOnly, RequireAuth } from './auth/RouteGuards'
import { AppLayout, AuthLayout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'

const loadJobMatchesPage = () => import('./pages/JobMatchesPage').then((m) => ({ default: m.JobMatchesPage }))
const JobMatchesPage = lazy(loadJobMatchesPage)
const loadJobsLayout = () => import('./pages/JobsLayout').then((m) => ({ default: m.JobsLayout }))
const JobsLayout = lazy(loadJobsLayout)
const loadProfilePage = () => import('./pages/ProfilePage').then((m) => ({ default: m.ProfilePage }))
const ProfilePage = lazy(loadProfilePage)
const loadResumePage = () => import('./pages/ResumePage').then((m) => ({ default: m.ResumePage }))
const ResumePage = lazy(loadResumePage)
const loadSearchProfilePage = () => import('./pages/SearchProfilePage').then((m) => ({ default: m.SearchProfilePage }))
const SearchProfilePage = lazy(loadSearchProfilePage)
const loadVacanciesPage = () => import('./pages/VacanciesPage').then((m) => ({ default: m.VacanciesPage }))
const VacanciesPage = lazy(loadVacanciesPage)

const preload = () => [loadJobMatchesPage, loadJobsLayout, loadProfilePage, loadResumePage, loadSearchProfilePage, loadVacanciesPage].forEach((load) => void load())
window.addEventListener('load', () => setTimeout(preload, 1000), { once: true })

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
