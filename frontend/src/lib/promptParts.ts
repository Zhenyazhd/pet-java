import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import type { PromptPart } from '../api/types'
import { errorMessage, isAbortError } from './abort'

export const PROMPT_MAX = 6000

export function usePromptParts() {
  const [parts, setParts] = useState<PromptPart[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setLoadError(null)
    api
      .getPromptParts(controller.signal)
      .then(setParts)
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
    setParts((prev) => prev.map((p) => (p.key === next.key ? next : p)))
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
