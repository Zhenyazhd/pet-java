import { useEffect, useState } from 'react'
import { api, type Profile } from '../api/client'
import { Banner } from '../components/ui/Banner'
import { Button } from '../components/ui/Button'
import { PageHeader } from '../components/ui/PageHeader'
import { ProfileField } from '../components/ui/ProfileField'

export function ProfilePage() {
  const [profile, setProfile] = useState<Profile>({
    displayName: '',
    email: '',
    careerPath: '',
  })
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError(null)
      try {
        const data = await api.getProfile()
        if (!cancelled) setProfile(data)
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load profile')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  function update<K extends keyof Profile>(key: K, value: Profile[K]) {
    setProfile((prev) => ({ ...prev, [key]: value }))
    setSaved(false)
  }

  async function save() {
    setSaving(true)
    setError(null)
    setSaved(false)
    try {
      const data = await api.saveProfile(profile)
      setProfile(data)
      setSaved(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save profile')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="page profile-page">
      <PageHeader
        eyebrow="Account"
        title="My Profile"
        lead="Your identity and career narrative — used as context when AI edits the resume."
        actions={
          <Button onClick={save} disabled={loading || saving}>
            {saving ? 'Saving…' : 'Save'}
          </Button>
        }
      />

      {error && <Banner tone="error">{error}</Banner>}
      {saved && !error && <Banner tone="ok">Saved.</Banner>}

      <div className="profile-identity">
        <ProfileField
          label="Name"
          value={profile.displayName}
          onChange={(e) => update('displayName', e.target.value)}
          disabled={loading}
          placeholder="Your name"
          autoComplete="name"
        />
        <ProfileField
          label="Email"
          type="email"
          value={profile.email}
          onChange={(e) => update('email', e.target.value)}
          disabled={loading}
          placeholder="you@example.com"
          autoComplete="email"
        />
      </div>

      <div className="profile-career-section">
        <div className="profile-career-section__intro">
          <span className="profile-field__label">Career path</span>
          <p className="profile-career-section__hint">
            Describe your background in full — roles, projects, studies, skills and transitions.
            The more complete this story is, the better AI can draft and tailor your CV later.
          </p>
        </div>
        <div className="profile-career">
          <textarea
            className="career-editor"
            value={profile.careerPath}
            onChange={(e) => update('careerPath', e.target.value)}
            disabled={loading}
            placeholder="Example: 2019–2022 backend engineer at X… then moved to Y… studied Z… currently looking for…"
            spellCheck={true}
            aria-label="Career path"
          />
        </div>
      </div>
    </section>
  )
}
