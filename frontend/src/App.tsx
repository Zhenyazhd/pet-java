import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { ProfilePage } from './pages/ProfilePage'
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
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<ResumePage />} />
            <Route path="profile" element={<ProfilePage />} />
            <Route path="career" element={<Navigate to="/profile" replace />} />
            <Route path="vacancies" element={<VacancyListPage />} />
            <Route path="vacancies/new" element={<VacancyCreatePage />} />
            <Route path="vacancies/:id" element={<VacancyDetailPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
