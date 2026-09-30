import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { GuestOnly, RequireAuth } from './auth/RequireAuth'
import { AuthLayout, Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { ProfilePage } from './pages/ProfilePage'
import { RegisterPage } from './pages/RegisterPage'
import { ResumePage } from './pages/ResumePage'
import { VacancyCreatePage } from './pages/VacancyCreatePage'
import { VacancyDetailPage } from './pages/VacancyDetailPage'
import { VacancyListPage } from './pages/VacancyListPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      retry: 1,
    },
  },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter
        future={{
          v7_startTransition: true,
          v7_relativeSplatPath: true,
        }}
      >
        <AuthProvider>
          <Routes>
            <Route element={<GuestOnly />}>
              <Route element={<AuthLayout />}>
                <Route path="login" element={<LoginPage />} />
                <Route path="register" element={<RegisterPage />} />
              </Route>
            </Route>

            <Route element={<RequireAuth />}>
              <Route element={<Layout />}>
                <Route index element={<ResumePage />} />
                <Route path="profile" element={<ProfilePage />} />
                <Route path="career" element={<Navigate to="/profile" replace />} />
                <Route path="vacancies" element={<VacancyListPage />} />
                <Route path="vacancies/new" element={<VacancyCreatePage />} />
                <Route path="vacancies/:id" element={<VacancyDetailPage />} />
              </Route>
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
