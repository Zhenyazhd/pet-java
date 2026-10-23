import { useId } from 'react'
import type { Vacancy } from '../../api/types'
import { companyLabel, statusOf } from '../../lib/vacancies/vacancy'
import { Select } from '../ui/Select'

type VacancyPickerProps = {
  vacancies: Vacancy[]
  loading: boolean
  error: string | null
  value: number | null
  onChange: (id: number | null) => void
}

export function VacancyPicker({ vacancies, loading, error, value, onChange }: VacancyPickerProps) {
  const id = useId()
  // Only roles still to apply for are worth tailoring a CV to. The one already chosen stays listed
  // (a "Tailor my CV" link can point at any vacancy), or the select would show a choice it cannot display.
  const options = vacancies.filter((vacancy) => statusOf(vacancy) === 'NOT_APPLIED' || vacancy.id === value)
  const hint = error
    ? 'Your vacancies could not be loaded.'
    : loading
      ? 'Loading your vacancies…'
      : options.length === 0
        ? 'No vacancies waiting for an application. Add one on the Vacancies page.'
        : 'Only vacancies you have not applied to yet. The AI and the match check aim at the chosen one.'

  return (
    <div className="field">
      <label className="field__label small-caps" htmlFor={id}>
        Tailor for a vacancy
      </label>
      <Select
        id={id}
        className="input"
        aria-describedby={`${id}-hint`}
        value={value === null ? '' : String(value)}
        onChange={(next) => onChange(next ? Number(next) : null)}
        options={[
          { value: '', label: 'No vacancy' },
          ...options.map((vacancy) => ({ value: String(vacancy.id), label: `${vacancy.title} · ${companyLabel(vacancy)}` })),
        ]}
      />
      <p id={`${id}-hint`} className="field__hint">
        {hint}
      </p>
    </div>
  )
}
