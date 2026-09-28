import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import type { ChangeEvent, MouseEvent } from 'react'
import { api } from '../api/client'
import {
  APPLICATION_STATUSES,
  type ApplicationStatus,
  type Vacancy,
} from '../api/types'
import {
  buildVacancyContext,
  isNotApplied,
  stashPrepareVacancyContext,
} from '../lib/vacancyPrepare'

export function VacancyRow({ vacancy }: { vacancy: Vacancy }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const canPrepare = isNotApplied(vacancy)
  const currentStatus: ApplicationStatus = vacancy.application?.status ?? 'NOT_APPLIED'
  const statusTone =
    currentStatus === 'INTERVIEW'
      ? 'vacancy-row__status--interview'
      : currentStatus === 'REJECTED' || currentStatus === 'WITHDRAWN'
        ? 'vacancy-row__status--closed'
        : ''

  const statusMutation = useMutation({
    mutationFn: (status: ApplicationStatus) => {
      if (vacancy.application) {
        return api.updateApplication(vacancy.application.id, status)
      }
      return api.createApplication(vacancy.id, status)
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['vacancies'] })
    },
  })

  function onPrepareCv(event: MouseEvent) {
    event.preventDefault()
    event.stopPropagation()
    stashPrepareVacancyContext(buildVacancyContext(vacancy))
    navigate('/')
  }

  function onStatusChange(event: ChangeEvent<HTMLSelectElement>) {
    const next = event.target.value as ApplicationStatus
    if (next === currentStatus) return
    statusMutation.mutate(next)
  }

  return (
    <div className="vacancy-row">
      <Link to={`/vacancies/${vacancy.id}`} className="vacancy-row__main">
        <h2>{vacancy.title}</h2>
        <p>{vacancy.company ?? 'Company not set'}</p>
      </Link>
      <div className="vacancy-row__meta">
        <label className={`vacancy-row__status ${statusTone}`.trim()}>
          <span className="sr-only">Status</span>
          <select
            value={currentStatus}
            onChange={onStatusChange}
            disabled={statusMutation.isPending}
            aria-label={`Status for ${vacancy.title}`}
          >
            {APPLICATION_STATUSES.map((status) => (
              <option key={status} value={status}>
                {status.replace(/_/g, ' ')}
              </option>
            ))}
          </select>
        </label>
        <span className="match">
          {vacancy.matchPercent == null ? '—%' : `${vacancy.matchPercent}%`}
        </span>
        {canPrepare && (
          <button type="button" className="button button--ghost vacancy-row__prepare" onClick={onPrepareCv}>
            Prepare CV
          </button>
        )}
        {statusMutation.isError && (
          <p className="vacancy-row__error">{(statusMutation.error as Error).message}</p>
        )}
      </div>
    </div>
  )
}
