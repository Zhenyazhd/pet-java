import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'

export function VacancyCreatePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [url, setUrl] = useState('')
  const [pastedText, setPastedText] = useState('')
  const [formError, setFormError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: api.importVacancy,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['vacancies'] })
      navigate('/vacancies')
    },
    onError: (error: Error) => setFormError(error.message),
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)

    const trimmedUrl = url.trim()
    const trimmedPaste = pastedText.trim()
    if (!trimmedUrl) {
      setFormError('Job URL is required')
      return
    }
    if (!trimmedPaste) {
      setFormError('Paste the job description')
      return
    }

    mutation.mutate({
      url: trimmedUrl,
      pastedText: trimmedPaste,
    })
  }

  return (
    <section className="page narrow">
      <div className="page-header">
        <div>
          <p className="eyebrow">Capture</p>
          <h1>Add vacancy</h1>
          <p className="page-lead">
            Paste the posting link and text. AI extracts title, company, and description, then saves
            it as not applied.
          </p>
        </div>
      </div>

      <form className="form" onSubmit={onSubmit}>
        <label>
          Job URL
          <input
            required
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://..."
            disabled={mutation.isPending}
          />
        </label>

        <label>
          Job description (paste)
          <textarea
            required
            rows={14}
            value={pastedText}
            onChange={(e) => setPastedText(e.target.value)}
            placeholder="Paste the full job posting here…"
            disabled={mutation.isPending}
          />
        </label>

        {formError && <p className="banner banner--error">{formError}</p>}

        <div className="form-actions">
          <button type="submit" className="button" disabled={mutation.isPending}>
            {mutation.isPending ? 'Parsing & saving…' : 'Save vacancy'}
          </button>
        </div>
      </form>
    </section>
  )
}
