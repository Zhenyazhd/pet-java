import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { importVacancy } from '../../lib/vacancies/importVacancy'
import { Banner } from '../ui/Banner'
import { Button } from '../ui/Button'
import { Field, TextAreaField } from '../ui/Field'

const POSTING_MAX = 50_000
const URL_MAX = 2_000
const CONFLICT = 409

type AddVacancyFormProps = {
  onSaved: (vacancyId: number) => Promise<void>
  findSavedByUrl: (url: string) => number | null
}

export function AddVacancyForm({ onSaved, findSavedByUrl }: AddVacancyFormProps) {
  const [url, setUrl] = useState('')
  const [pastedText, setPastedText] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const abort = useRef<AbortController | null>(null)

  useEffect(() => () => abort.current?.abort(), [])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (pending || !url.trim() || !pastedText.trim()) return
    setError(null)
    setPending(true)
    abort.current = new AbortController()
    const trimmedUrl = url.trim()
    try {
      const vacancyId = await importVacancy(
        { url: trimmedUrl, pastedText: pastedText.trim() },
        abort.current.signal,
      )
      setUrl('')
      setPastedText('')
      await onSaved(vacancyId)
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return
      const savedId = err instanceof ApiError && err.status === CONFLICT ? findSavedByUrl(trimmedUrl) : null
      if (savedId !== null) {
        await onSaved(savedId)
        return
      }
      setError(err instanceof Error ? err.message : 'The vacancy could not be saved.')
    } finally {
      setPending(false)
    }
  }

  return (
    <form className="add-vacancy" onSubmit={onSubmit} aria-busy={pending}>
      <Field
        label="Vacancy link"
        type="url"
        inputMode="url"
        autoComplete="off"
        placeholder="https://company.com/jobs/backend-engineer"
        value={url}
        onChange={(e) => setUrl(e.target.value)}
        maxLength={URL_MAX}
        required
        readOnly={pending}
      />
      <TextAreaField
        label="Posting text"
        hint="Paste the whole posting. AI picks out the title, company, requirements and description to save."
        counter={`${pastedText.length.toLocaleString('en-US')} / ${POSTING_MAX.toLocaleString('en-US')}`}
        rows={10}
        value={pastedText}
        onChange={(e) => setPastedText(e.target.value)}
        maxLength={POSTING_MAX}
        required
        readOnly={pending}
      />
      {error && <Banner tone="error">{error}</Banner>}
      <div className="add-vacancy__actions">
        <Button type="submit" aria-disabled={pending || !url.trim() || !pastedText.trim()}>
          {pending ? 'Reading the posting…' : 'Save vacancy'}
        </Button>
        {/* Rendered even when empty: screen readers only announce changes inside an existing region. */}
        <p className="status-text add-vacancy__progress" role="status">
          {pending ? 'This can take a few minutes' : ''}
        </p>
      </div>
    </form>
  )
}
