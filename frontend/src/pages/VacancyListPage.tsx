import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { VacancyRow } from '../components/VacancyRow'

export function VacancyListPage() {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['vacancies'],
    queryFn: api.listVacancies,
  })

  return (
    <section className="page">
      <div className="page-header">
        <div>
          <p className="eyebrow">Your pipeline</p>
          <h1>Vacancies</h1>
        </div>
        <Link className="button" to="/vacancies/new">
          Add vacancy
        </Link>
      </div>

      {isLoading && <p className="muted">Loading…</p>}
      {isError && (
        <div className="banner banner--error">
          <p>{(error as Error).message}</p>
          <button type="button" className="button button--ghost" onClick={() => refetch()}>
            Retry
          </button>
        </div>
      )}

      {data && data.length === 0 && (
        <div className="empty">
          <h2>No vacancies yet</h2>
          <p>Paste a job URL and description — AI fills title and company, then tracks it as not applied.</p>
          <Link className="button" to="/vacancies/new">
            Add first vacancy
          </Link>
        </div>
      )}

      {data && data.length > 0 && (
        <div className="vacancy-list">
          {data.map((vacancy) => (
            <VacancyRow key={vacancy.id} vacancy={vacancy} />
          ))}
        </div>
      )}
    </section>
  )
}
