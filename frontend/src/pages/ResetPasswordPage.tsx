import { errorMessage } from '../lib/abort'
import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { Banner } from '../components/ui/Banner'
import { Button } from '../components/ui/Button'
import { Field } from '../components/ui/Field'
import { PageHeader } from '../components/ui/PageHeader'
import { MIN_PASSWORD_LENGTH, passwordProblem } from '../lib/password'
import { useDocumentTitle } from '../lib/useDocumentTitle'

export function ResetPasswordPage() {
  useDocumentTitle('Choose a new password')
  const token = useSearchParams()[0].get('token')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [done, setDone] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (pending || !token) return
    const problem = passwordProblem(password)
    if (problem) {
      setError(problem)
      return
    }
    setError(null)
    setPending(true)
    try {
      await api.resetPassword(token, password)
      setDone(true)
    } catch (err) {
      setError(errorMessage(err, 'Could not change the password'))
    } finally {
      setPending(false)
    }
  }

  if (done || !token) {
    return (
      <section className="auth-card" aria-labelledby="reset-title">
        <PageHeader
          eyebrow="Account"
          title={done ? 'Password changed' : 'This link is not complete'}
          titleId="reset-title"
          lead={done ? 'You can sign in with the new password.' : 'Open the link from the email again, or ask for a new one.'}
        />
        <p className="auth-switch">
          <Link to={done ? '/login' : '/forgot-password'}>{done ? 'Go to sign in' : 'Ask for a new link'}</Link>
        </p>
      </section>
    )
  }

  return (
    <section className="auth-card" aria-labelledby="reset-title">
      <PageHeader eyebrow="Account" title="Choose a new password" titleId="reset-title" />
      <form className="form" onSubmit={onSubmit} aria-busy={pending}>
        <Field
          label="New password"
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
          {pending ? 'Saving…' : 'Change the password'}
        </Button>
      </form>
      <p className="auth-switch">
        <Link to="/forgot-password">Ask for a new link</Link>
      </p>
    </section>
  )
}
