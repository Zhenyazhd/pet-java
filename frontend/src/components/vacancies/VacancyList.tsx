import { useState } from 'react'
import { APPLICATION_STATUSES, type ApplicationStatus, type Vacancy } from '../../api/types'
import { formatApplicationStatus } from '../../lib/vacancies/applicationStatus'
import { statusOf } from '../../lib/vacancies/vacancy'
import { Banner } from '../ui/Banner'
import { Button } from '../ui/Button'
import { VacancyRow } from './VacancyRow'

type Filter = 'ALL' | ApplicationStatus

type VacancyListProps = {
  vacancies: Vacancy[]
  loading: boolean
  error: string | null
  onRetry: () => void
  onStatusChange: (vacancy: Vacancy, status: ApplicationStatus) => Promise<void>
}

const FILTERS: Filter[] = ['ALL', ...APPLICATION_STATUSES]

function matchesQuery(vacancy: Vacancy, query: string): boolean {
  const needle = query.trim().toLowerCase()
  return (
    !needle ||
    vacancy.title.toLowerCase().includes(needle) ||
    (vacancy.company ?? '').toLowerCase().includes(needle)
  )
}

export function VacancyList({ vacancies, loading, error, onRetry, onStatusChange }: VacancyListProps) {
  const [filter, setFilter] = useState<Filter>('ALL')
  const [query, setQuery] = useState('')

  const searched = vacancies.filter((vacancy) => matchesQuery(vacancy, query))

  const counts = Object.fromEntries(FILTERS.map((f) => [f, 0])) as Record<Filter, number>
  for (const vacancy of searched) {
    counts.ALL += 1
    counts[statusOf(vacancy)] += 1
  }

  const visible = filter === 'ALL' ? searched : searched.filter((v) => statusOf(v) === filter)

  if (error && vacancies.length === 0) {
    return (
      <div className="stack">
        <Banner tone="error">{error}</Banner>
        <div>
          <Button variant="outline" onClick={onRetry}>
            Try again
          </Button>
        </div>
      </div>
    )
  }

  if (loading && vacancies.length === 0) {
    return (
      <p className="status-text muted-line" role="status">Loading vacancies…</p>
    )
  }

  if (vacancies.length === 0) {
    return (
      <div className="empty">
        <h3>Nothing saved yet</h3>
        <p>Paste a link and the posting text above. The first vacancy will appear here.</p>
      </div>
    )
  }

  return (
    <div className="stack">
      {error && (
        <div className="stack">
          <Banner tone="error">{error}</Banner>
          <div>
            <Button variant="outline" onClick={onRetry}>
              Try again
            </Button>
          </div>
        </div>
      )}
      <div className="vacancy-toolbar">
        <input
          className="input"
          type="search"
          placeholder="Search by title or company"
          aria-label="Search vacancies"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          autoComplete="off"
        />
        <div className="chips" role="group" aria-label="Filter by status">
          {FILTERS.map((f) => (
            <button
              key={f}
              type="button"
              className="chip"
              aria-pressed={filter === f}
              onClick={() => setFilter(f)}
            >
              {f === 'ALL' ? 'All' : formatApplicationStatus(f)}
              <span className="chip__count">{counts[f]}</span>
            </button>
          ))}
        </div>
      </div>

      {visible.length === 0 ? (
        <div className="empty">
          <h3>No matches</h3>
          <p>Nothing fits this search and status filter.</p>
          <Button
            variant="ghost"
            onClick={() => {
              setFilter('ALL')
              setQuery('')
            }}
          >
            Clear filters
          </Button>
        </div>
      ) : (
        <ul className="vacancy-list">
          {visible.map((vacancy) => (
            <VacancyRow key={vacancy.id} vacancy={vacancy} onStatusChange={onStatusChange} />
          ))}
        </ul>
      )}
    </div>
  )
}
