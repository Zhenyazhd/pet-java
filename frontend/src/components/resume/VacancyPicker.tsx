import { useId } from 'react'
import type { Vacancy } from '../../api/types'
import { companyLabel } from '../../lib/vacancies/vacancy'

type VacancyPickerProps = {
  vacancies: Vacancy[]
  loading: boolean
  error: string | null
  value: number | null
  onChange: (id: number | null) => void
}

export function VacancyPicker({ vacancies, loading, error, value, onChange }: VacancyPickerProps) {
  const id = useId()
  const hint = error
    ? 'Your vacancies could not be loaded.'
    : loading
      ? 'Loading your vacancies…'
      : 'AI suggestions and the match check target this role. Changing it clears the chat and the report, not your edits.'

  return (
    <div className="field">
      <label className="field__label small-caps" htmlFor={id}>
        Tailor for a vacancy
      </label>
      <select
        id={id}
        className="input"
        aria-describedby={`${id}-hint`}
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}
      >
        <option value="">No vacancy</option>
        {vacancies.map((vacancy) => (
          <option key={vacancy.id} value={vacancy.id}>
            {vacancy.title} · {companyLabel(vacancy)}
          </option>
        ))}
      </select>
      <p id={`${id}-hint`} className="field__hint">
        {hint}
      </p>
    </div>
  )
}
