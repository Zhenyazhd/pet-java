import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { ApplicationStatus, Vacancy } from '../../api/types'
import { formatDate } from '../../lib/format'
import { companyLabel, statusOf } from '../../lib/vacancies/vacancy'
import { StatusSelect } from './StatusSelect'

type VacancyRowProps = {
  vacancy: Vacancy
  onStatusChange: (vacancy: Vacancy, status: ApplicationStatus) => Promise<void>
}

export function VacancyRow({ vacancy, onStatusChange }: VacancyRowProps) {
  const [error, setError] = useState<string | null>(null)

  async function change(next: ApplicationStatus) {
    setError(null)
    try {
      await onStatusChange(vacancy, next)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not change the status')
    }
  }

  return (
    <li className="vacancy-row">
      <Link to={`/vacancies/${vacancy.id}`} className="vacancy-row__main">
        <span className="vacancy-row__title">{vacancy.title}</span>
        <span className="vacancy-row__meta small-caps">
          {companyLabel(vacancy)} · Added {formatDate(vacancy.createdAt)}
        </span>
      </Link>
      <StatusSelect value={statusOf(vacancy)} onChange={change} label={`Status for ${vacancy.title}`} />
      {error && (
        <p className="vacancy-row__error" role="alert">
          {error}
        </p>
      )}
    </li>
  )
}
