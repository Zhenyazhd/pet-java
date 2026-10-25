import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { AuthConfig } from '../api/types'

let cached: Promise<AuthConfig> | null = null

/** What the server offers before anyone signs in (for now: Google). Null while it is being asked; asked once per page load. */
export function useAuthConfig(): AuthConfig | null {
  const [config, setConfig] = useState<AuthConfig | null>(null)

  useEffect(() => {
    let cancelled = false
    cached ??= api.getAuthConfig().catch(() => {
      // Not knowing means not offering: the password form works without it. The next visit asks again.
      cached = null
      return { google: false }
    })
    void cached.then((value) => !cancelled && setConfig(value))
    return () => {
      cancelled = true
    }
  }, [])

  return config
}
