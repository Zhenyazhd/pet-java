import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import type { PromptPart } from '../api/types'
import { errorMessage, isAbortError } from './abort'
import { recall, remember } from './pageCache'

export const PROMPT_MAX = 6000

const CACHE_KEY = 'promptParts'

export function usePromptParts() {
  const [parts, setParts] = useState<PromptPart[]>(() => recall<PromptPart[]>(CACHE_KEY) ?? [])
  const [loading, setLoading] = useState(parts.length === 0)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    if (!recall(CACHE_KEY)) setLoading(true)
    setLoadError(null)
    api
      .getPromptParts(controller.signal)
      .then((next) => {
        setParts(next)
        remember(CACHE_KEY, next)
      })
      .catch((err) => {
        if (!isAbortError(err)) setLoadError(errorMessage(err, 'Failed to load the instructions'))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [attempt])

  const retry = useCallback(() => setAttempt((n) => n + 1), [])

  const replace = useCallback((next: PromptPart) => {
    setParts((prev) => {
      const updated = prev.map((p) => (p.key === next.key ? next : p))
      remember(CACHE_KEY, updated)
      return updated
    })
  }, [])

  const save = useCallback(
    async (key: string, text: string) => {
      const updated = await api.savePromptPart(key, text)
      replace(updated)
      return updated
    },
    [replace],
  )

  const reset = useCallback(
    async (part: PromptPart) => {
      await api.resetPromptPart(part.key)
      replace({ ...part, customText: null, customized: false, defaultChanged: false })
    },
    [replace],
  )

  return { parts, loading, loadError, retry, save, reset }
}
