import { Link } from 'react-router-dom'
import type { Vacancy } from '../api/types'

function statusLabel(vacancy: Vacancy): string {
  if (!vacancy.application) return 'Not tracked'
  return vacancy.application.status.replace(/_/g, ' ')
}

export function VacancyRow({ vacancy }: { vacancy: Vacancy }) {
  return (
    <Link to={`/vacancies/${vacancy.id}`} className="vacancy-row">
      <div className="vacancy-row__main">
        <h2>{vacancy.title}</h2>
        <p>{vacancy.company ?? 'Company not set'}</p>
      </div>
      <div className="vacancy-row__meta">
        <span className={`pill ${vacancy.application?.applied ? 'pill--ok' : ''}`}>
          {statusLabel(vacancy)}
        </span>
        <span className="match">
          {vacancy.matchPercent == null ? '—%' : `${vacancy.matchPercent}%`}
        </span>
      </div>
    </Link>
  )
}
