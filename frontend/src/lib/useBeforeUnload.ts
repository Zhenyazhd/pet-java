import { useEffect } from 'react'

/** Asks the browser to confirm closing the tab while there is something unsaved. */
export function useBeforeUnload(active: boolean) {
  useEffect(() => {
    if (!active) return
    const warn = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [active])
}
