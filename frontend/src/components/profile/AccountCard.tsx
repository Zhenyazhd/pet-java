import { useState, type FormEvent } from 'react'
import { api } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../../lib/abort'
import { Banner } from '../ui/Banner'
import { Button } from '../ui/Button'
import { Dialog } from '../ui/Dialog'
import { Field } from '../ui/Field'

const DELETE_TITLE_ID = 'delete-account-title'

export function AccountCard() {
  const { user } = useAuth()
  if (!user) return null
  return (
    <section className="account" aria-labelledby="account-title">
      <hr className="rule" />
      <h2 id="account-title" className="prompts__title">
        Password and account
      </h2>
      <ChangePassword email={user.email} hasPassword={user.hasPassword !== false} />
      {user.role !== 'ADMIN' && <DeleteAccount email={user.email} hasPassword={user.hasPassword !== false} />}
    </section>
  )
}

/** The same mailed link as "forgot password": only whoever reads the mailbox can choose the new password. */
function ChangePassword({ email, hasPassword }: { email: string; hasPassword: boolean }) {
  const [pending, setPending] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function send() {
    if (pending) return
    setPending(true)
    setError(null)
    try {
      await api.forgotPassword(email)
      setSent(true)
    } catch (err) {
      setError(errorMessage(err, 'Could not send the link'))
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="stack">
      <h3 className="prompts__name">{hasPassword ? 'Change password' : 'Add a password'}</h3>
      <p className="field__hint">
        {hasPassword
          ? `We email a link to ${email}. Open it to choose a new password.`
          : `You sign in with Google. We can email a link to ${email} so you can also sign in with a password.`}{' '}
        The link works once and expires in an hour.
      </p>
      {error && <Banner tone="error">{error}</Banner>}
      <div role="status">{sent && <Banner tone="ok">Link sent to {email}. Check your inbox.</Banner>}</div>
      <div>
        <Button variant="outline" aria-disabled={pending} onClick={() => void send()}>
          {pending ? 'Sending…' : sent ? 'Send again' : 'Email me a link'}
        </Button>
      </div>
    </div>
  )
}

function DeleteAccount({ email, hasPassword }: { email: string; hasPassword: boolean }) {
  const { logout } = useAuth()
  const [open, setOpen] = useState(false)
  const [typedEmail, setTypedEmail] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function close() {
    if (pending) return
    setOpen(false)
    setTypedEmail('')
    setPassword('')
    setError(null)
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (pending) return
    setPending(true)
    setError(null)
    try {
      await api.deleteAccount({ email: typedEmail.trim(), password: hasPassword ? password : undefined })
      await logout() // the server ended the session already; this clears what the browser holds
    } catch (err) {
      setError(errorMessage(err, 'Could not delete the account'))
      setPending(false)
    }
  }

  return (
    <div className="stack">
      <h3 className="prompts__name">Delete account</h3>
      <p className="field__hint">
        Removes your profile, CV, vacancies, matches and saved instructions for good. This cannot be undone.
      </p>
      <div>
        <Button variant="outline" className="btn--danger" onClick={() => setOpen(true)}>
          Delete my account…
        </Button>
      </div>
      {open && (
        <Dialog labelledBy={DELETE_TITLE_ID} onClose={close}>
          <header className="dialog__header">
            <h2 id={DELETE_TITLE_ID} className="dialog__title">
              Delete account
            </h2>
          </header>
          <form className="form" onSubmit={onSubmit} aria-busy={pending}>
            <p className="field__hint">
              Everything you saved here is deleted immediately and cannot be recovered. To confirm, type your email
              address
              {hasPassword ? ' and your password' : ''}.
            </p>
            <Field
              label={`Type ${email}`}
              type="email"
              autoComplete="off"
              value={typedEmail}
              onChange={(e) => setTypedEmail(e.target.value)}
              required
            />
            {hasPassword && (
              <Field
                label="Password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            )}
            {error && <Banner tone="error">{error}</Banner>}
            <div className="profile__actions">
              <Button type="submit" variant="outline" className="btn--danger" aria-disabled={pending}>
                {pending ? 'Deleting…' : 'Delete account for good'}
              </Button>
              <Button variant="ghost" aria-disabled={pending} onClick={close}>
                Cancel
              </Button>
            </div>
          </form>
        </Dialog>
      )}
    </div>
  )
}
