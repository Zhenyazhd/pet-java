import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import type { Requirement } from '../api/types'

type RequirementDraft = Requirement & { key: string }

function newRequirement(): RequirementDraft {
  return { key: crypto.randomUUID(), name: '', required: true }
}

export function VacancyCreatePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [url, setUrl] = useState('')
  const [title, setTitle] = useState('')
  const [company, setCompany] = useState('')
  const [description, setDescription] = useState('')
  const [matchPercent, setMatchPercent] = useState('')
  const [requirements, setRequirements] = useState<RequirementDraft[]>([newRequirement()])
  const [formError, setFormError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: api.createVacancy,
    onSuccess: async (vacancy) => {
      await queryClient.invalidateQueries({ queryKey: ['vacancies'] })
      navigate(`/vacancies/${vacancy.id}`)
    },
    onError: (error: Error) => setFormError(error.message),
  })

  function updateRequirement(key: string, patch: Partial<Requirement>) {
    setRequirements((current) =>
      current.map((item) => (item.key === key ? { ...item, ...patch } : item)),
    )
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)

    const cleaned = requirements
      .map((item) => ({ name: item.name.trim(), required: item.required }))
      .filter((item) => item.name.length > 0)

    const percent = matchPercent.trim() === '' ? null : Number(matchPercent)
    if (percent != null && (Number.isNaN(percent) || percent < 0 || percent > 100)) {
      setFormError('Match percent must be between 0 and 100')
      return
    }

    mutation.mutate({
      url: url.trim(),
      title: title.trim(),
      company: company.trim() || undefined,
      description: description.trim() || undefined,
      matchPercent: percent,
      requirements: cleaned,
    })
  }

  return (
    <section className="page narrow">
      <div className="page-header">
        <div>
          <p className="eyebrow">Capture</p>
          <h1>Add vacancy</h1>
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
          />
        </label>

        <label>
          Title
          <input required value={title} onChange={(e) => setTitle(e.target.value)} />
        </label>

        <label>
          Company
          <input value={company} onChange={(e) => setCompany(e.target.value)} />
        </label>

        <label>
          Description
          <textarea
            rows={4}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </label>

        <label>
          Match percent
          <input
            type="number"
            min={0}
            max={100}
            value={matchPercent}
            onChange={(e) => setMatchPercent(e.target.value)}
            placeholder="0–100"
          />
        </label>

        <fieldset className="requirements">
          <legend>Requirements</legend>
          {requirements.map((item) => (
            <div key={item.key} className="requirement-row">
              <input
                value={item.name}
                onChange={(e) => updateRequirement(item.key, { name: e.target.value })}
                placeholder="Java, Kafka…"
              />
              <label className="checkbox">
                <input
                  type="checkbox"
                  checked={item.required}
                  onChange={(e) => updateRequirement(item.key, { required: e.target.checked })}
                />
                Required
              </label>
              <button
                type="button"
                className="button button--ghost"
                onClick={() =>
                  setRequirements((current) => current.filter((row) => row.key !== item.key))
                }
                disabled={requirements.length === 1}
              >
                Remove
              </button>
            </div>
          ))}
          <button
            type="button"
            className="button button--ghost"
            onClick={() => setRequirements((current) => [...current, newRequirement()])}
          >
            Add requirement
          </button>
        </fieldset>

        {formError && <p className="banner banner--error">{formError}</p>}

        <div className="form-actions">
          <button type="submit" className="button" disabled={mutation.isPending}>
            {mutation.isPending ? 'Saving…' : 'Save vacancy'}
          </button>
        </div>
      </form>
    </section>
  )
}
