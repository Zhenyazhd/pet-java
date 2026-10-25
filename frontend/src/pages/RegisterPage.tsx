import { errorMessage } from '../lib/abort'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { GoogleButton } from '../components/auth/GoogleButton'
import { Banner } from '../components/ui/Banner'
import { Button } from '../components/ui/Button'
import { Field } from '../components/ui/Field'
import { PageHeader } from '../components/ui/PageHeader'
import { MIN_PASSWORD_LENGTH, passwordProblem } from '../lib/password'
import { useDocumentTitle } from '../lib/useDocumentTitle'

export function RegisterPage() {
  useDocumentTitle('Create account')

  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [inviteCode, setInviteCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  // The address the confirmation link was sent to; the form gives way to this once the account exists.
  const [sentTo, setSentTo] = useState<string | null>(null)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (pending) return
    setError(null)

    const invite = inviteCode.trim()
    if (!invite) {
      setError('An invite code is required.')
      return
    }
    const problem = passwordProblem(password)
    if (problem) {
      setError(problem)
      return
    }

    setPending(true)
    try {
      const registered = await api.register({
        email: email.trim(),
        password,
        displayName: displayName.trim() || email.trim().split('@')[0] || 'User',
        inviteCode: invite,
      })
      setSentTo(registered.email)
    } catch (err) {
      setError(errorMessage(err, 'Registration failed'))
    } finally {
      setPending(false)
    }
  }

  if (sentTo) {
    return (
      <section className="auth-card" aria-labelledby="register-title">
        <PageHeader
          eyebrow="One more step"
          title="Check your email"
          titleId="register-title"
          lead={`We sent a link to ${sentTo}. Open it to confirm your address, then sign in. The link works for 24 hours.`}
        />
        <p className="auth-switch">
          <Link to="/login">Go to sign in</Link>
        </p>
      </section>
    )
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
          label="Invite code"
          type="text"
          autoComplete="off"
          spellCheck={false}
          value={inviteCode}
          onChange={(e) => setInviteCode(e.target.value)}
          required
          hint="Needed whichever way you sign up."
        />
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
        {error && <Banner tone="error">{error}</Banner>}
        <Button type="submit" block aria-disabled={pending}>
          {pending ? 'Creating account…' : 'Create account'}
        </Button>
        <GoogleButton
          label="Sign up with Google"
          prepare={async () => {
            const invite = inviteCode.trim()
            if (!invite) throw new Error('Enter your invite code first.')
            await api.googleStart(invite)
          }}
        />
      </form>
      <p className="auth-switch">
        Already registered? <Link to="/login">Sign in</Link>
      </p>
    </section>
  )
}
