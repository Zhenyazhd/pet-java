import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { APPLICATION_STATUSES, type ApplicationStatus } from '../api/types'

export function VacancyDetailPage() {
  const { id } = useParams()
  const vacancyId = Number(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<ApplicationStatus>('APPLIED')
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)

  const { data, isLoading, isError, error: loadError } = useQuery({
    queryKey: ['vacancies', vacancyId],
    queryFn: () => api.getVacancy(vacancyId),
    enabled: Number.isFinite(vacancyId),
  })

  useEffect(() => {
    if (data?.application) {
      setStatus(data.application.status)
    }
  }, [data])

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['vacancies'] })
    await queryClient.invalidateQueries({ queryKey: ['vacancies', vacancyId] })
  }

  const applyMutation = useMutation({
    mutationFn: () => {
      if (!data) throw new Error('Vacancy not loaded')
      if (data.application) {
        return api.updateApplication(data.application.id, status, notes)
      }
      return api.createApplication(data.id, status, notes)
    },
    onSuccess: async () => {
      setError(null)
      await invalidate()
    },
    onError: (err: Error) => setError(err.message),
  })

  const deleteMutation = useMutation({
    mutationFn: () => api.deleteVacancy(vacancyId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['vacancies'] })
      navigate('/')
    },
    onError: (err: Error) => setError(err.message),
  })

  function onSubmitApplication(event: FormEvent) {
    event.preventDefault()
    applyMutation.mutate()
  }

  if (!Number.isFinite(vacancyId)) {
    return <p className="banner banner--error">Invalid vacancy id</p>
  }

  if (isLoading) return <p className="muted">Loading…</p>
  if (isError || !data) {
    return <p className="banner banner--error">{(loadError as Error)?.message ?? 'Not found'}</p>
  }

  return (
    <section className="page">
      <div className="page-header">
        <div>
          <p className="eyebrow">{data.company ?? 'Vacancy'}</p>
          <h1>{data.title}</h1>
          <a className="external" href={data.url} target="_blank" rel="noreferrer">
            Open original posting
          </a>
        </div>
        <div className="header-actions">
          <span className="match match--lg">
            {data.matchPercent == null ? '—%' : `${data.matchPercent}%`}
          </span>
          <button
            type="button"
            className="button button--danger"
            onClick={() => {
              if (confirm('Delete this vacancy?')) deleteMutation.mutate()
            }}
          >
            Delete
          </button>
        </div>
      </div>

      {data.description && (
        <div className="panel">
          <h2>Description</h2>
          <p className="prewrap">{data.description}</p>
        </div>
      )}

      <div className="panel">
        <h2>Requirements</h2>
        {data.requirements.length === 0 ? (
          <p className="muted">No requirements saved</p>
        ) : (
          <ul className="req-list">
            {data.requirements.map((item) => (
              <li key={item.id ?? item.name}>
                <span>{item.name}</span>
                <span className="pill">{item.required ? 'required' : 'optional'}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="panel">
        <h2>Application</h2>
        <p className="muted">
          Current:{' '}
          {data.application
            ? `${data.application.status.replace(/_/g, ' ')} (${data.application.applied ? 'applied' : 'not applied'})`
            : 'not tracked yet'}
        </p>

        <form className="form compact" onSubmit={onSubmitApplication}>
          <label>
            Status
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as ApplicationStatus)}
            >
              {APPLICATION_STATUSES.map((item) => (
                <option key={item} value={item}>
                  {item.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
          </label>
          <label>
            Notes
            <textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} />
          </label>
          {error && <p className="banner banner--error">{error}</p>}
          <button type="submit" className="button" disabled={applyMutation.isPending}>
            {data.application ? 'Update status' : 'Save application'}
          </button>
        </form>
      </div>

      <Link className="back-link" to="/">
        ← Back to list
      </Link>
    </section>
  )
}
