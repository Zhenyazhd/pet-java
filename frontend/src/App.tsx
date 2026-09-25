import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
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
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<VacancyListPage />} />
            <Route path="vacancies/new" element={<VacancyCreatePage />} />
            <Route path="vacancies/:id" element={<VacancyDetailPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
