import { Link, useNavigate } from 'react-router-dom'
import type { MouseEvent } from 'react'
import type { Vacancy } from '../api/types'
import {
  buildVacancyContext,
  isNotApplied,
  stashPrepareVacancyContext,
} from '../lib/vacancyPrepare'

function statusLabel(vacancy: Vacancy): string {
  if (!vacancy.application) return 'Not tracked'
  return vacancy.application.status.replace(/_/g, ' ')
}

export function VacancyRow({ vacancy }: { vacancy: Vacancy }) {
  const navigate = useNavigate()
  const canPrepare = isNotApplied(vacancy)

  function onPrepareCv(event: MouseEvent) {
    event.preventDefault()
    event.stopPropagation()
    stashPrepareVacancyContext(buildVacancyContext(vacancy))
    navigate('/')
  }

  return (
    <div className="vacancy-row">
      <Link to={`/vacancies/${vacancy.id}`} className="vacancy-row__main">
        <h2>{vacancy.title}</h2>
        <p>{vacancy.company ?? 'Company not set'}</p>
      </Link>
      <div className="vacancy-row__meta">
        <span className={`pill ${vacancy.application?.applied ? 'pill--ok' : ''}`}>
          {statusLabel(vacancy)}
        </span>
        <span className="match">
          {vacancy.matchPercent == null ? '—%' : `${vacancy.matchPercent}%`}
        </span>
        {canPrepare && (
          <button type="button" className="button button--ghost vacancy-row__prepare" onClick={onPrepareCv}>
            Prepare CV
          </button>
        )}
      </div>
    </div>
  )
}
