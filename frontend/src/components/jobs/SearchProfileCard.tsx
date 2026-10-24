import { formatCounter } from '../../lib/format'
import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import type { JobSearchProfile, JobSearchProfileInput } from '../../api/types'
import { EXPECTATIONS_MAX, toValues } from '../../lib/jobSearch'
import { errorMessage } from '../../lib/abort'
import type { useProfileForm } from '../../lib/useProfileForm'
import { Banner } from '../ui/Banner'
import { Button } from '../ui/Button'
import { Field, TextAreaField } from '../ui/Field'
import { RegionsEditor } from './RegionsEditor'
import { RolesEditor } from './RolesEditor'

type SearchProfileCardProps = {
  form: ReturnType<typeof useProfileForm>
  hasProfile: boolean
  onSave: (input: JobSearchProfileInput) => Promise<JobSearchProfile>
  /** Runs after the profile is saved (or was already saved and unchanged). */
  onSaveAndSearch: () => Promise<void>
  /** Why a search cannot start now, or null. */
  searchBlocker: string | null
  /** False while the form still shows a cached copy: saving it could overwrite a newer profile. */
  ready: boolean
}

export function SearchProfileCard({
  form,
  hasProfile,
  onSave,
  onSaveAndSearch,
  searchBlocker,
  ready,
}: SearchProfileCardProps) {
  const { values, change } = form
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  // The error belongs to the form as it was submitted: editing it makes the message stale.
  useEffect(() => setSaveError(null), [values])

  // A first profile can be saved as it is; afterwards only a change is worth a request.
  const needsSave = form.dirty || !hasProfile
  const canSave = ready && form.valid && !saving && needsSave
  const canSaveAndSearch = ready && form.valid && !saving && searchBlocker === null

  async function submit(thenSearch: boolean) {
    if (!form.valid) form.attempt()
    if (!(thenSearch ? canSaveAndSearch : canSave)) return
    setSaving(true)
    setSaveError(null)
    try {
      if (needsSave) {
        const saved = await onSave({ ...values, expectations: values.expectations.trim() })
        form.reset(toValues(saved))
      }
      if (thenSearch) await onSaveAndSearch()
    } catch (err) {
      setSaveError(errorMessage(err, 'The search profile could not be saved.'))
    } finally {
      setSaving(false)
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    void submit(false)
  }

  return (
    <section className="job-card" aria-label="Search profile form">
      <form className="job-form" onSubmit={onSubmit} aria-busy={saving}>
        <TextAreaField
          label="What you are looking for"
          hint="Seniority, stack, kind of company, what to avoid. The fuller, the better the ranking."
          placeholder="Example: Senior backend engineer, Java or Kotlin, product company with fewer than 500 people. Remote or hybrid. No consulting or outsourcing."
          counter={`${formatCounter(values.expectations.length, EXPECTATIONS_MAX)}`}
          rows={5}
          value={values.expectations}
          maxLength={EXPECTATIONS_MAX}
          readOnly={saving}
          error={form.expectationsError}
          errorAlert={form.attempted}
          onBlur={() => form.touch('expectations')}
          onChange={(e) => change({ expectations: e.target.value })}
        />
        <RolesEditor
          roles={values.roles}
          draft={form.roleDraft}
          onDraft={form.setRoleDraft}
          onAdd={() => form.addRole()}
          onRemove={(i) => change({ roles: values.roles.filter((_, k) => k !== i) })}
          error={form.rolesError}
          note={form.roleNote}
          alert={form.attempted}
          onTouch={() => form.touch('roles')}
          disabled={saving}
        />
        <RegionsEditor
          regions={values.regions}
          country={form.country}
          city={form.city}
          onCountry={form.setCountry}
          onCity={form.setCity}
          onAdd={() => form.addRegion()}
          onRemove={(i) => change({ regions: values.regions.filter((_, k) => k !== i) })}
          error={form.regionsError}
          note={form.regionNote}
          alert={form.attempted}
          disabled={saving}
        />
        <Field
          label="Minimum score"
          hint="Only matches scoring at least this (0 to 100) are kept. A higher number means fewer, closer matches; lower it if you see too few. New profiles start at 50."
          type="number"
          inputMode="numeric"
          min={0}
          max={100}
          step={1}
          className="input input--score"
          value={Number.isNaN(values.minScore) ? '' : values.minScore}
          readOnly={saving}
          error={form.scoreError}
          errorAlert={form.attempted}
          onChange={(e) =>
            change({
              minScore: e.target.value === '' ? NaN : Number(e.target.value),
            })
          }
        />

        {saveError && <Banner tone="error">{saveError}</Banner>}
        <div role="status">
          {form.justSaved && !saveError && (
            <Banner tone="ok">Search profile saved. Your matches use it from now on.</Banner>
          )}
        </div>
        <div className="job-form__actions">
          <Button type="submit" aria-disabled={!canSave}>
            {saving ? 'Saving…' : 'Save profile'}
          </Button>
          {form.dirty && !saving && <span className="status-text">Unsaved changes</span>}
        </div>
        {form.justSaved && !saveError && (
          <div>
            <Link to="/jobs" className="btn btn--primary">
              Back to job matches
            </Link>
          </div>
        )}
        <div className="job-form__actions">
          <Button
            variant="outline"
            aria-disabled={!canSaveAndSearch}
            aria-describedby="save-search-help"
            onClick={() => void submit(true)}
          >
            Save and search now
          </Button>
          <span id="save-search-help" className="field__hint">
            {searchBlocker ?? 'Saves, then starts a search. It costs money and is limited per day.'}
          </span>
        </div>
      </form>
    </section>
  )
}
