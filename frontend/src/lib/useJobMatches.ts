import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import type { JobMatch } from '../api/types'
import { errorMessage, isAbortError } from './abort'

type Filters = { minScore: number; seen: boolean }

/** Matches for the filters, newest page first; `refreshKey` reloads the first page (e.g. after a run). */
export function useJobMatches({ minScore, seen }: Filters, refreshKey: number) {
  const [items, setItems] = useState<JobMatch[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [moreError, setMoreError] = useState<string | null>(null)
  const [savedIds, setSavedIds] = useState<ReadonlySet<JobMatch['id']>>(new Set())
  const [attempt, setAttempt] = useState(0)
  const abort = useRef<AbortController | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    abort.current?.abort()
    abort.current = controller
    setLoading(true)
    setLoadingMore(false)
    setError(null)
    setMoreError(null)
    api
      .listJobMatches({ minScore, seen }, controller.signal)
      .then((page) => {
        setItems(page.items)
        setNextCursor(page.nextCursor)
      })
      .catch((err) => {
        if (!isAbortError(err)) setError(errorMessage(err, 'Failed to load matches'))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [minScore, seen, refreshKey, attempt])

  const loadMore = useCallback(() => {
    if (!nextCursor || loadingMore || loading) return
    const controller = abort.current
    if (!controller) return
    setLoadingMore(true)
    setMoreError(null)
    api
      .listJobMatches({ minScore, seen, cursor: nextCursor }, controller.signal)
      .then((page) => {
        setItems((prev) => {
          const known = new Set(prev.map((m) => m.id))
          return [...prev, ...page.items.filter((m) => !known.has(m.id))]
        })
        setNextCursor(page.nextCursor)
      })
      .catch((err) => {
        if (!isAbortError(err)) setMoreError(errorMessage(err, 'Failed to load more matches'))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadingMore(false)
      })
  }, [nextCursor, loadingMore, loading, minScore, seen])

  const patch = (id: JobMatch['id'], deliveredAt: string | null) =>
    setItems((prev) => prev.map((m) => (m.id === id ? { ...m, deliveredAt } : m)))

  /** Optimistic: muted at once, put back when the server refuses. */
  const markSeen = useCallback(async (id: JobMatch['id']) => {
    patch(id, new Date().toISOString())
    try {
      await api.markJobMatchSeen(id)
    } catch (err) {
      patch(id, null)
      throw err
    }
  }, [])

  const markSaved = useCallback((id: JobMatch['id']) => {
    setSavedIds((prev) => new Set(prev).add(id))
  }, [])

  const retry = useCallback(() => setAttempt((n) => n + 1), [])

  return {
    retry,
    items,
    hasMore: nextCursor !== null,
    loading,
    loadingMore,
    error,
    moreError,
    savedIds,
    loadMore,
    markSeen,
    markSaved,
  }
}
