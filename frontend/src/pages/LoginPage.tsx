import { errorMessage } from '../lib/abort'
import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, isEmailNotVerified } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { GoogleButton } from '../components/auth/GoogleButton'
import { Banner } from '../components/ui/Banner'
import { Button } from '../components/ui/Button'
import { Field } from '../components/ui/Field'
import { PageHeader } from '../components/ui/PageHeader'
import { useDocumentTitle } from '../lib/useDocumentTitle'

/** Why the trip to Google ended without signing in; the server puts one of these keys in the address. */
const GOOGLE_PROBLEMS: Record<string, string> = {
  unverified: 'Google did not confirm that email address, so it cannot be used to sign in.',
  invite_required:
    'There is no account for that Google address yet. Register with an invite code first and choose Google there.',
  invite_invalid: 'That invite code is not valid, or it was already used.',
  failed: 'Signing in with Google did not complete. Please try again.',
}

export function LoginPage() {
  useDocumentTitle('Sign in')
  const { login } = useAuth()
  const [params] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(() => GOOGLE_PROBLEMS[params.get('google') ?? ''] ?? null)
  const [unconfirmed, setUnconfirmed] = useState(false)
  const [resent, setResent] = useState(false)
  const [pending, setPending] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (pending) return
    setError(null)
    setUnconfirmed(false)
    setResent(false)
    setPending(true)
    try {
      await login({ email: email.trim(), password })
    } catch (err) {
      setUnconfirmed(isEmailNotVerified(err))
      setError(errorMessage(err, 'Sign-in failed'))
    } finally {
      setPending(false)
    }
  }

  async function resend() {
    if (pending) return
    setPending(true)
    try {
      await api.resendVerification(email.trim())
      setResent(true)
    } catch (err) {
      setError(errorMessage(err, 'Could not send the link'))
    } finally {
      setPending(false)
    }
  }

  return (
    <section className="auth-card" aria-labelledby="login-title">
      <PageHeader eyebrow="Welcome back" title="Sign in" titleId="login-title" />
      <form className="form" onSubmit={onSubmit} aria-busy={pending}>
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
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {error && (
          <Banner tone="error">
            <p>{error}</p>
            {unconfirmed && (
              <Button variant="outline" aria-disabled={pending || resent} onClick={() => void resend()}>
                {resent ? 'Link sent' : 'Send the link again'}
              </Button>
            )}
          </Banner>
        )}
        <Button type="submit" block aria-disabled={pending}>
          {pending ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
      <p className="field__hint">
        <Link to="/forgot-password">Forgot your password?</Link>
      </p>
      <GoogleButton label="Continue with Google" />
      <p className="auth-switch">
        No account yet? <Link to="/register">Register with an invite code</Link>
      </p>
    </section>
  )
}
