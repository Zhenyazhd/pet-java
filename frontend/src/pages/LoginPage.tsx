import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Banner } from '../components/ui/Banner'
import { Button } from '../components/ui/Button'
import { Field } from '../components/ui/Field'
import { PageHeader } from '../components/ui/PageHeader'
import { useDocumentTitle } from '../lib/useDocumentTitle'

export function LoginPage() {
  useDocumentTitle('Sign in')
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (pending) return
    setError(null)
    setPending(true)
    try {
      await login({ email: email.trim(), password })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign-in failed')
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
          minLength={8}
        />
        {error && <Banner tone="error">{error}</Banner>}
        <Button type="submit" block aria-disabled={pending}>
          {pending ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
      <p className="auth-switch">
        No account yet? <Link to="/register">Register with an invite code</Link>
      </p>
    </section>
  )
}
