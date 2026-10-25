import { errorMessage } from '../lib/abort'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { Banner } from '../components/ui/Banner'
import { Button } from '../components/ui/Button'
import { Field } from '../components/ui/Field'
import { PageHeader } from '../components/ui/PageHeader'
import { useDocumentTitle } from '../lib/useDocumentTitle'

export function ForgotPasswordPage() {
  useDocumentTitle('Reset your password')
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [sent, setSent] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (pending) return
    setError(null)
    setPending(true)
    try {
      await api.forgotPassword(email.trim())
      setSent(true)
    } catch (err) {
      setError(errorMessage(err, 'Could not send the link'))
    } finally {
      setPending(false)
    }
  }

  return (
    <section className="auth-card" aria-labelledby="forgot-title">
      <PageHeader
        eyebrow="Account"
        title="Reset your password"
        titleId="forgot-title"
        lead={
          sent
            ? 'If an account exists for that address, a link is on its way. It works once and expires in one hour.'
            : 'Enter your email address and we will send you a link to choose a new password.'
        }
      />
      {!sent && (
        <form className="form" onSubmit={onSubmit} aria-busy={pending}>
          <Field
            label="Email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          {error && <Banner tone="error">{error}</Banner>}
          <Button type="submit" block aria-disabled={pending}>
            {pending ? 'Sending…' : 'Send the link'}
          </Button>
        </form>
      )}
      <p className="auth-switch">
        <Link to="/login">Back to sign in</Link>
      </p>
    </section>
  )
}
