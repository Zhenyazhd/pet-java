import { useEffect } from 'react'

/** Moves focus to the page title after navigating, so screen readers start from the new page. */
export function useFocusHeading(id: string) {
  useEffect(() => {
    document.getElementById(id)?.focus({ preventScroll: true })
  }, [id])
}
