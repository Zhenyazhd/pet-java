import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api } from '../api/client'
import type { Profile } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Banner } from '../components/ui/Banner'
import { Button } from '../components/ui/Button'
import { Field, TextAreaField } from '../components/ui/Field'
import { PageHeader } from '../components/ui/PageHeader'
import { useDocumentTitle } from '../lib/useDocumentTitle'

const CAREER_PATH_MAX = 20_000
const EMPTY: Profile = { displayName: '', email: '', careerPath: '' }

export function ProfilePage() {
  useDocumentTitle('Profile')
  const { updateUser } = useAuth()
  const [profile, setProfile] = useState<Profile>(EMPTY)
  const [saved, setSaved] = useState<Profile>(EMPTY)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [justSaved, setJustSaved] = useState(false)

  const dirty =
    profile.displayName !== saved.displayName ||
    profile.email !== saved.email ||
    profile.careerPath !== saved.careerPath

  const load = useCallback(() => {
    let cancelled = false
    setLoading(true)
    setLoadError(null)
    api
      .getProfile()
      .then((data) => {
        if (cancelled) return
        setProfile(data)
        setSaved(data)
      })
      .catch((err) => {
        if (!cancelled)
          setLoadError(err instanceof Error ? err.message : 'Failed to load the profile')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => load(), [load])

  useEffect(() => {
    if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  function update<K extends keyof Profile>(key: K, value: Profile[K]) {
    setProfile((prev) => ({ ...prev, [key]: value }))
    setJustSaved(false)
  }

  const disabled = loading || saving

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (disabled || !dirty) return
    setSaving(true)
    setSaveError(null)
    setJustSaved(false)
    try {
      const data = await api.saveProfile({
        ...profile,
        displayName: profile.displayName.trim(),
        email: profile.email.trim(),
      })
      setProfile(data)
      setSaved(data)
      updateUser({ displayName: data.displayName, email: data.email })
      setJustSaved(true)
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : 'Failed to save the profile')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="page" aria-labelledby="profile-title">
      <PageHeader
        eyebrow="Account"
        title="Your profile"
        titleId="profile-title"
        lead="Who you are and where you have been. This is the context AI will draw on when it drafts and tailors your resume."
      />

      {loadError ? (
        <div className="stack">
          <Banner tone="error">{loadError}</Banner>
          <div>
            <Button variant="outline" onClick={load}>
              Try again
            </Button>
          </div>
        </div>
      ) : (
        <form className="profile" onSubmit={onSubmit} aria-busy={loading}>
          <div className="profile__identity">
            <Field
              label="Name"
              type="text"
              autoComplete="name"
              value={profile.displayName}
              onChange={(e) => update('displayName', e.target.value)}
              disabled={loading}
              readOnly={saving}
              maxLength={255}
              required
            />
            <Field
              label="Email"
              type="email"
              autoComplete="email"
              value={profile.email}
              onChange={(e) => update('email', e.target.value)}
              disabled={loading}
              readOnly={saving}
              maxLength={255}
              required
            />
          </div>

          <hr className="rule" />

          <TextAreaField
            label="Career path"
            hint="Roles, projects, studies, skills and the turns between them. The fuller the story, the better the drafts."
            counter={`${profile.careerPath.length.toLocaleString('en-US')} / ${CAREER_PATH_MAX.toLocaleString('en-US')}`}
            value={profile.careerPath}
            onChange={(e) => update('careerPath', e.target.value)}
            disabled={loading}
              readOnly={saving}
            maxLength={CAREER_PATH_MAX}
            placeholder="2019–2022 backend engineer at X, then moved to Y after studying Z. Now looking for…"
          />

          {saveError && <Banner tone="error">{saveError}</Banner>}
          <div role="status">
            {justSaved && !saveError && <Banner tone="ok">Profile saved.</Banner>}
          </div>

          <div className="profile__actions">
            <Button type="submit" aria-disabled={disabled || !dirty}>
              {saving ? 'Saving…' : 'Save profile'}
            </Button>
            {dirty && !saving && <span className="status-text profile__dirty">Unsaved changes</span>}
          </div>
        </form>
      )}
    </section>
  )
}
