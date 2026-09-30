import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import type { ChangeEvent, MouseEvent } from 'react'
import { api } from '../api/client'
import {
  APPLICATION_STATUSES,
  type ApplicationStatus,
  type Vacancy,
} from '../api/types'
import { isNotApplied, prepareCvAndNavigate } from '../lib/vacancyPrepare'
import { invalidateVacancy } from '../lib/vacancyQueries'
import { formatApplicationStatus, vacancyRowStatusClass } from '../lib/applicationStatus'
import { Button } from './ui/Button'

export function VacancyRow({ vacancy }: { vacancy: Vacancy }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const canPrepare = isNotApplied(vacancy)
  const currentStatus: ApplicationStatus = vacancy.application?.status ?? 'NOT_APPLIED'
  const statusTone = vacancyRowStatusClass(currentStatus)

  const statusMutation = useMutation({
    mutationFn: (status: ApplicationStatus) => {
      if (vacancy.application) {
        return api.updateApplication(vacancy.application.id, status)
      }
      return api.createApplication(vacancy.id, status)
    },
    onSuccess: () => invalidateVacancy(queryClient, vacancy.id),
  })

  function onPrepareCv(event: MouseEvent) {
    event.preventDefault()
    event.stopPropagation()
    prepareCvAndNavigate(vacancy, navigate)
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
                {formatApplicationStatus(status)}
              </option>
            ))}
          </select>
        </label>
        <span className="match">
          {vacancy.matchPercent == null ? '—%' : `${vacancy.matchPercent}%`}
        </span>
        {canPrepare && (
          <Button variant="ghost" className="vacancy-row__prepare" onClick={onPrepareCv}>
            Prepare CV
          </Button>
        )}
        {statusMutation.isError && (
          <p className="vacancy-row__error">{(statusMutation.error as Error).message}</p>
        )}
      </div>
    </div>
  )
}
