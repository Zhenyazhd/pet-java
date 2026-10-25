import { useState } from 'react'
import { errorMessage } from '../../lib/abort'
import { useAuthConfig } from '../../lib/useAuthConfig'
import { AuthDivider } from './AuthDivider'
import { Banner } from '../ui/Banner'
import { Button } from '../ui/Button'

/** The server starts the trip to Google at this address and brings the browser back to the app signed in. */
const GOOGLE_PATH = '/oauth2/authorization/google'

type GoogleButtonProps = {
  label: string
  /** Runs before leaving, e.g. to hand the server an invite code; a throw stops the trip and is shown. */
  prepare?: () => Promise<void>
}

export function GoogleButton({ label, prepare }: GoogleButtonProps) {
  const config = useAuthConfig()
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  if (!config?.google) return null

  async function leave() {
    if (pending) return
    setPending(true)
    setError(null)
    try {
      await prepare?.()
      window.location.assign(GOOGLE_PATH)
    } catch (err) {
      setError(errorMessage(err, 'Could not start sign-in with Google'))
      setPending(false)
    }
  }

  return (
    <div className="auth-google">
      <AuthDivider>or</AuthDivider>
      <Button variant="outline" block aria-disabled={pending} onClick={() => void leave()}>
        {pending ? 'Opening Google…' : label}
      </Button>
      {error && <Banner tone="error">{error}</Banner>}
    </div>
  )
}
