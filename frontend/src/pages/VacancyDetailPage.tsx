import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { APPLICATION_STATUSES, type ApplicationStatus } from '../api/types'
import { isNotApplied, prepareCvAndNavigate } from '../lib/vacancyPrepare'
import { invalidateVacancy } from '../lib/vacancyQueries'
import { formatApplicationStatus } from '../lib/applicationStatus'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Banner } from '../components/ui/Banner'

export function VacancyDetailPage() {
  const { id } = useParams()
  const vacancyId = Number(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<ApplicationStatus>('APPLIED')
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)
  // Tracks unsaved edits so a background refetch (e.g. refetchOnWindowFocus)
  // can't silently clobber notes/status the user hasn't submitted yet.
  const [dirty, setDirty] = useState(false)

  const { data, isLoading, isError, error: loadError } = useQuery({
    queryKey: ['vacancies', vacancyId],
    queryFn: () => api.getVacancy(vacancyId),
    enabled: Number.isFinite(vacancyId),
  })

  useEffect(() => {
    if (data?.application && !dirty) {
      setStatus(data.application.status)
      setNotes(data.application.notes ?? '')
    }
  }, [data, dirty])

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
      setDirty(false)
      await invalidateVacancy(queryClient, vacancyId)
    },
    onError: (err: Error) => setError(err.message),
  })

  const deleteMutation = useMutation({
    mutationFn: () => api.deleteVacancy(vacancyId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['vacancies'] })
      // Drop detail cache so browser Back cannot briefly show the deleted vacancy.
      queryClient.removeQueries({ queryKey: ['vacancies', vacancyId] })
      navigate('/vacancies')
    },
    onError: (err: Error) => setError(err.message),
  })

  function onSubmitApplication(event: FormEvent) {
    event.preventDefault()
    applyMutation.mutate()
  }

  if (!Number.isFinite(vacancyId)) {
    return <Banner tone="error">Invalid vacancy id</Banner>
  }

  if (isLoading) return <p className="muted">Loading…</p>
  if (isError || !data) {
    return <Banner tone="error">{(loadError as Error)?.message ?? 'Not found'}</Banner>
  }

  return (
    <section className="page">
      <PageHeader
        eyebrow={data.company ?? 'Vacancy'}
        title={data.title}
        actions={
          <>
            <span className="match match--lg">
              {data.matchPercent == null ? '—%' : `${data.matchPercent}%`}
            </span>
            {isNotApplied(data) && (
              <Button onClick={() => prepareCvAndNavigate(data, navigate)}>Prepare CV</Button>
            )}
            <Button
              className="button--danger"
              onClick={() => {
                if (confirm('Delete this vacancy?')) deleteMutation.mutate()
              }}
            >
              Delete
            </Button>
          </>
        }
      >
        <a className="external" href={data.url} target="_blank" rel="noreferrer">
          Open original posting
        </a>
      </PageHeader>

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
            ? `${formatApplicationStatus(data.application.status)} (${data.application.applied ? 'applied' : 'not applied'})`
            : 'not tracked yet'}
        </p>

        <form className="form compact" onSubmit={onSubmitApplication}>
          <label>
            Status
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as ApplicationStatus)
                setDirty(true)
              }}
            >
              {APPLICATION_STATUSES.map((item) => (
                <option key={item} value={item}>
                  {formatApplicationStatus(item)}
                </option>
              ))}
            </select>
          </label>
          <label>
            Notes
            <textarea
              rows={3}
              value={notes}
              onChange={(e) => {
                setNotes(e.target.value)
                setDirty(true)
              }}
            />
          </label>
          {error && <Banner tone="error">{error}</Banner>}
          <Button type="submit" disabled={applyMutation.isPending}>
            {data.application ? 'Update status' : 'Save application'}
          </Button>
        </form>
      </div>

      <Link className="back-link" to="/vacancies">
        ← Back to list
      </Link>
    </section>
  )
}
