import { useId, useRef, type KeyboardEvent } from 'react'
import type { JobRegion } from '../../api/types'
import { CITY_MAX, REGIONS_MAX, regionLabel } from '../../lib/jobSearch'
import { Button } from '../ui/Button'

type RegionsEditorProps = {
  regions: JobRegion[]
  country: string
  city: string
  onCountry: (value: string) => void
  onCity: (value: string) => void
  onAdd: () => void
  onRemove: (index: number) => void
  error: string | null
  note: string | null
  alert: boolean
  disabled: boolean
}

export function RegionsEditor(props: RegionsEditorProps) {
  const { regions, country, city, onCountry, onCity, onAdd, onRemove, error, note, alert, disabled } = props
  const id = useId()
  const countryInput = useRef<HTMLInputElement>(null)
  const message = note ?? error

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Enter') return
    event.preventDefault()
    onAdd()
  }

  return (
    <div className="field">
      <div className="field__row">
        <span className="field__label small-caps" id={`${id}-label`}>
          Regions
        </span>
        <span className="field__counter small-caps">
          {regions.length} / {REGIONS_MAX}
        </span>
      </div>
      <p className="field__hint">
        Optional. A 2-letter country code (for example FR) and, if you like, a city. Put the most important place first:
        LinkedIn and Welcome to the Jungle use only the first region; HelloWork uses only the first French city.
      </p>
      {regions.length > 0 && (
        <ul className="tag-list" aria-labelledby={`${id}-label`}>
          {regions.map((region, index) => (
            <li key={regionLabel(region)} className="tag">
              <span className="tag__text">{regionLabel(region)}</span>
              <button
                type="button"
                className="tag__remove"
                aria-label={`Remove region ${regionLabel(region)}`}
                disabled={disabled}
                onClick={() => {
                  onRemove(index)
                  countryInput.current?.focus()
                }}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="add-row add-row--region">
        <input
          ref={countryInput}
          className="input input--country"
          aria-label="Country code"
          placeholder="DE"
          value={country}
          maxLength={2}
          autoComplete="off"
          autoCapitalize="characters"
          aria-describedby={message ? `${id}-msg` : undefined}
          aria-invalid={error !== null}
          readOnly={disabled}
          onChange={(e) => onCountry(e.target.value)}
          onKeyDown={onKeyDown}
        />
        <input
          className="input"
          aria-label="City (optional)"
          placeholder="City (optional)"
          value={city}
          maxLength={CITY_MAX}
          autoComplete="off"
          readOnly={disabled}
          onChange={(e) => onCity(e.target.value)}
          onKeyDown={onKeyDown}
        />
        <Button
          variant="outline"
          aria-disabled={disabled || !(country || city.trim())}
          onClick={() => !disabled && onAdd()}
        >
          Add
        </Button>
      </div>
      {message && (
        <p id={`${id}-msg`} className="field__error" role={alert ? 'alert' : undefined}>
          {message}
        </p>
      )}
    </div>
  )
}
