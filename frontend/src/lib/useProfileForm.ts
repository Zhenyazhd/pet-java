import { useState } from 'react'
import type { JobRegion } from '../api/types'
import {
  CITY_MAX,
  REGIONS_MAX,
  ROLES_MAX,
  ROLE_LENGTH_MAX,
  isValidScore,
  sameValues,
  type ProfileValues,
} from './jobSearch'

const COUNTRY = /^[A-Z]{2}$/

/** Editable copy of the search profile with the half-typed "add" inputs and a message per field. */
export function useProfileForm(saved: ProfileValues) {
  const [values, setValues] = useState<ProfileValues>(saved)
  const [roleDraft, setRoleDraft] = useState('')
  const [country, setCountry] = useState('')
  const [city, setCity] = useState('')
  const [roleNote, setRoleNote] = useState<string | null>(null)
  const [regionNote, setRegionNote] = useState<string | null>(null)
  // Messages appear once a field was visited or a save was tried, not on the first render.
  const [touched, setTouched] = useState({ expectations: false, roles: false })
  const [attempted, setAttempted] = useState(false)
  const [wasSaved, setSaved] = useState(false)
  const touch = (field: 'expectations' | 'roles') => setTouched((prev) => ({ ...prev, [field]: true }))

  const change = (next: Partial<ProfileValues>) => setValues((prev) => ({ ...prev, ...next }))

  function addRole() {
    const role = roleDraft.trim()
    if (!role) return
    if (values.roles.length >= ROLES_MAX) return setRoleNote(`At most ${ROLES_MAX} roles.`)
    if (values.roles.some((r) => r.toLowerCase() === role.toLowerCase())) {
      return setRoleNote(`“${role}” is already in the list.`)
    }
    change({ roles: [...values.roles, role] })
    setRoleDraft('')
    setRoleNote(null)
  }

  function addRegion() {
    if (!country && !city.trim()) return
    if (!COUNTRY.test(country)) return
    if (values.regions.length >= REGIONS_MAX) return setRegionNote(`At most ${REGIONS_MAX} regions.`)
    const region: JobRegion = { country, city: city.trim() || null }
    if (values.regions.some((r) => r.country === region.country && r.city?.toLowerCase() === region.city?.toLowerCase())) {
      return setRegionNote('That region is already in the list.')
    }
    change({ regions: [...values.regions, region] })
    setCountry('')
    setCity('')
    setRegionNote(null)
  }

  const rolesProblem =
    values.roles.length === 0 && !roleDraft.trim()
      ? 'Add at least one role.'
      : roleDraft.trim()
        ? `Press Add (or Enter) to include “${roleDraft.trim()}”, or clear the field.`
        : null

  const regionsError =
    country || city.trim()
      ? COUNTRY.test(country)
        ? 'Press Add to include this region, or clear the fields.'
        : 'Use a 2-letter country code, for example DE.'
      : null

  const expectationsProblem = values.expectations.trim() ? null : 'Describe what you are looking for.'

  const scoreError = isValidScore(values.minScore) ? null : 'Enter a whole number from 0 to 100.'
  const valid = !expectationsProblem && !rolesProblem && !regionsError && !scoreError
  const pending = Boolean(roleDraft.trim() || country || city.trim())
  const dirty = !sameValues(values, saved) || pending

  return {
    values,
    change,
    roleDraft,
    setRoleDraft: (v: string) => (setRoleDraft(v.slice(0, ROLE_LENGTH_MAX)), setRoleNote(null)),
    addRole,
    country,
    setCountry: (v: string) => (
      setCountry(
        v
          .toUpperCase()
          .replace(/[^A-Z]/g, '')
          .slice(0, 2),
      ),
      setRegionNote(null)
    ),
    city,
    setCity: (v: string) => setCity(v.slice(0, CITY_MAX)),
    addRegion,
    touch,
    attempted,
    attempt: () => setAttempted(true),
    rolesError: touched.roles || attempted ? rolesProblem : null,
    expectationsError: touched.expectations || attempted ? expectationsProblem : null,
    roleNote,
    regionsError,
    regionNote,
    scoreError,
    valid,
    dirty,
    /** True from a save until the next edit. */
    justSaved: wasSaved && !dirty,
    /** After a save the server copy is the new baseline. */
    reset: (next: ProfileValues) => {
      setSaved(true)
      setAttempted(false)
      setValues(next)
      setRoleDraft('')
      setCountry('')
      setCity('')
    },
  }
}
