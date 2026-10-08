import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Banner } from '../components/ui/Banner'
import { Button } from '../components/ui/Button'
import { Field } from '../components/ui/Field'
import { PageHeader } from '../components/ui/PageHeader'
import { useDocumentTitle } from '../lib/useDocumentTitle'

const MIN_PASSWORD_LENGTH = 8
const MAX_PASSWORD_BYTES = 72

export function RegisterPage() {
  useDocumentTitle('Create account')
  const { register } = useAuth()

  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [inviteCode, setInviteCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (pending) return
    setError(null)

    const invite = inviteCode.trim()
    if (!invite) {
      setError('An invite code is required.')
      return
    }
    if (new TextEncoder().encode(password).length > MAX_PASSWORD_BYTES) {
      setError('Password is too long — use at most 72 bytes (about 72 ASCII characters).')
      return
    }

    setPending(true)
    try {
      await register({
        email: email.trim(),
        password,
        displayName: displayName.trim() || email.trim().split('@')[0] || 'User',
        inviteCode: invite,
      })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed')
    } finally {
      setPending(false)
    }
  }

  return (
    <section className="auth-card" aria-labelledby="register-title">
      <PageHeader
        eyebrow="Invite only"
        title="Create account"
        titleId="register-title"
        lead="Registration needs a one-time invite code. Ask the project owner if you do not have one."
      />
      <form className="form" onSubmit={onSubmit} aria-busy={pending}>
        <Field
          label="Name"
          type="text"
          autoComplete="name"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          maxLength={255}
          placeholder="Optional"
        />
        <Field
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <Field
          label="Password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          minLength={MIN_PASSWORD_LENGTH}
          hint="At least 8 characters."
        />
        <Field
          label="Invite code"
          type="text"
          autoComplete="off"
          spellCheck={false}
          value={inviteCode}
          onChange={(e) => setInviteCode(e.target.value)}
          required
        />
        {error && <Banner tone="error">{error}</Banner>}
        <Button type="submit" block aria-disabled={pending}>
          {pending ? 'Creating account…' : 'Create account'}
        </Button>
      </form>
      <p className="auth-switch">
        Already registered? <Link to="/login">Sign in</Link>
      </p>
    </section>
  )
}
