import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { APPLICATION_STATUSES, type ApplicationStatus, type Vacancy } from '../api/types'
import { VacancyRow } from '../components/VacancyRow'

type StatusFilter = 'ALL' | ApplicationStatus

function vacancyStatus(vacancy: Vacancy): ApplicationStatus {
  return vacancy.application?.status ?? 'NOT_APPLIED'
}

function statusLabel(status: StatusFilter): string {
  if (status === 'ALL') return 'All'
  return status.replace(/_/g, ' ')
}

function filterToneClass(status: StatusFilter): string {
  if (status === 'INTERVIEW') return 'status-filter__chip--interview'
  if (status === 'REJECTED' || status === 'WITHDRAWN') return 'status-filter__chip--closed'
  return ''
}

function matchesCompany(vacancy: Vacancy, query: string): boolean {
  const normalized = query.trim().toLowerCase()
  if (!normalized) return true
  return (vacancy.company ?? '').toLowerCase().includes(normalized)
}

export function VacancyListPage() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [companyQuery, setCompanyQuery] = useState('')
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['vacancies'],
    queryFn: api.listVacancies,
  })

  const byCompany = useMemo(() => {
    if (!data) return []
    return data.filter((vacancy) => matchesCompany(vacancy, companyQuery))
  }, [data, companyQuery])

  const counts = useMemo(() => {
    const next: Record<StatusFilter, number> = {
      ALL: 0,
      NOT_APPLIED: 0,
      APPLIED: 0,
      INTERVIEW: 0,
      OFFER: 0,
      REJECTED: 0,
      WITHDRAWN: 0,
    }
    for (const vacancy of byCompany) {
      next.ALL += 1
      next[vacancyStatus(vacancy)] += 1
    }
    return next
  }, [byCompany])

  const filtered = useMemo(() => {
    if (statusFilter === 'ALL') return byCompany
    return byCompany.filter((vacancy) => vacancyStatus(vacancy) === statusFilter)
  }, [byCompany, statusFilter])

  const filterOptions: StatusFilter[] = ['ALL', ...APPLICATION_STATUSES]
  const hasCompanyQuery = companyQuery.trim().length > 0

  function clearFilters() {
    setStatusFilter('ALL')
    setCompanyQuery('')
  }

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
        <>
          <div className="vacancy-toolbar">
            <label className="vacancy-search">
              <span className="sr-only">Search by company</span>
              <input
                type="search"
                value={companyQuery}
                onChange={(e) => setCompanyQuery(e.target.value)}
                placeholder="Search by company…"
                autoComplete="off"
              />
            </label>
            <div className="status-filter" role="toolbar" aria-label="Filter by status">
              {filterOptions.map((status) => {
                const active = statusFilter === status
                const tone = filterToneClass(status)
                return (
                  <button
                    key={status}
                    type="button"
                    className={`status-filter__chip ${tone} ${active ? 'is-active' : ''}`.trim()}
                    aria-pressed={active}
                    onClick={() => setStatusFilter(status)}
                  >
                    <span>{statusLabel(status)}</span>
                    <span className="status-filter__count">{counts[status]}</span>
                  </button>
                )
              })}
            </div>
          </div>

          {filtered.length === 0 ? (
            <div className="empty">
              <h2>No matching vacancies</h2>
              <p>
                {hasCompanyQuery
                  ? 'Nothing matches this company search and status filter.'
                  : 'Try another status filter or reset to All.'}
              </p>
              <button type="button" className="button button--ghost" onClick={clearFilters}>
                Clear filters
              </button>
            </div>
          ) : (
            <div className="vacancy-list">
              {filtered.map((vacancy) => (
                <VacancyRow key={vacancy.id} vacancy={vacancy} />
              ))}
            </div>
          )}
        </>
      )}
    </section>
  )
}
