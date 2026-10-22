import { useId, useRef } from 'react'
import type { JobRegion } from '../../api/types'
import { CITY_MAX, REGIONS_MAX, regionLabel } from '../../lib/jobSearch'
import { Button } from '../ui/Button'
import { TagEditor, enterToAdd } from '../ui/TagEditor'

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

  return (
    <TagEditor
      id={id}
      label="Regions"
      counter={`${regions.length} / ${REGIONS_MAX}`}
      hint="Optional. A 2-letter country code (for example FR) and, if you like, a city. Put the most important place first: LinkedIn and Welcome to the Jungle use only the first region; HelloWork uses only the first French city."
      tags={regions.map(regionLabel)}
      noun="region"
      disabled={disabled}
      onRemove={onRemove}
      focusAfterRemove={countryInput}
      message={message}
      alert={alert}
    >
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
          onKeyDown={enterToAdd(onAdd)}
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
          onKeyDown={enterToAdd(onAdd)}
        />
        <Button
          variant="outline"
          aria-disabled={disabled || !(country || city.trim())}
          onClick={() => !disabled && onAdd()}
        >
          Add
        </Button>
      </div>
    </TagEditor>
  )
}
