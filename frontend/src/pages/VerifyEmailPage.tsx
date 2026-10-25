import { errorMessage } from '../lib/abort'
import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { PageHeader } from '../components/ui/PageHeader'
import { useDocumentTitle } from '../lib/useDocumentTitle'

type State = { status: 'working' } | { status: 'done' } | { status: 'failed'; message: string }

/** The page behind the link in the confirmation mail: spends the token once, then says how it went. */
export function VerifyEmailPage() {
  useDocumentTitle('Confirm your email')
  const token = useSearchParams()[0].get('token')
  const [state, setState] = useState<State>(
    token ? { status: 'working' } : { status: 'failed', message: 'This link is not complete.' },
  )
  // A link works once: React's development double render must not spend it twice.
  const sent = useRef(false)

  useEffect(() => {
    if (!token || sent.current) return
    sent.current = true
    api
      .verifyEmail(token)
      .then(() => setState({ status: 'done' }))
      .catch((err) => setState({ status: 'failed', message: errorMessage(err, 'Could not confirm the address') }))
  }, [token])

  return (
    <section className="auth-card" aria-labelledby="verify-title" aria-busy={state.status === 'working'}>
      <PageHeader
        eyebrow="Account"
        title={state.status === 'done' ? 'Email confirmed' : state.status === 'working' ? 'Confirming…' : 'Could not confirm'}
        titleId="verify-title"
        lead={
          state.status === 'done'
            ? 'Your address is confirmed. You can sign in now.'
            : state.status === 'failed'
              ? state.message
              : undefined
        }
      />
      <p className="auth-switch" role="status">
        {state.status === 'done' && <Link to="/login">Go to sign in</Link>}
        {state.status === 'failed' && <Link to="/login">Go to sign in to ask for a new link</Link>}
      </p>
    </section>
  )
}
