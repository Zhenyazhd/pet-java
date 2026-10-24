import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import type { JobMatch } from '../api/types'
import { errorMessage, isAbortError } from './abort'
import { recall, remember } from './pageCache'

type Filters = { minScore: number }
type FirstPage = { items: JobMatch[]; nextCursor: string | null }

const cacheKey = ({ minScore }: Filters) => `matches:${minScore}`

/** Matches for the filters, newest page first; `refreshKey` reloads the first page (e.g. after a run). */
export function useJobMatches({ minScore }: Filters, refreshKey: number) {
  const [cached] = useState(() => recall<FirstPage>(cacheKey({ minScore })))
  const [items, setItems] = useState<JobMatch[]>(cached?.items ?? [])
  const [nextCursor, setNextCursor] = useState<string | null>(cached?.nextCursor ?? null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [moreError, setMoreError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const itemsRef = useRef(items)
  itemsRef.current = items
  const abort = useRef<AbortController | null>(null)
  const shownKey = useRef(cacheKey({ minScore }))

  useEffect(() => {
    const controller = new AbortController()
    abort.current?.abort()
    abort.current = controller
    // New filters: show their last known first page (or nothing), never the previous filters' list.
    const key = cacheKey({ minScore })
    if (shownKey.current !== key) {
      shownKey.current = key
      const hit = recall<FirstPage>(key)
      setItems(hit?.items ?? [])
      setNextCursor(hit?.nextCursor ?? null)
    }
    setLoading(true)
    setLoadingMore(false)
    setError(null)
    setMoreError(null)
    api
      .listJobMatches({ minScore, seen: false }, controller.signal)
      .then((page) => {
        setItems(page.items)
        setNextCursor(page.nextCursor)
        remember(cacheKey({ minScore }), { items: page.items, nextCursor: page.nextCursor })
      })
      .catch((err) => {
        if (!isAbortError(err)) setError(errorMessage(err, 'Failed to load matches'))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [minScore, refreshKey, attempt])

  const loadMore = useCallback(() => {
    if (!nextCursor || loadingMore || loading) return
    const controller = abort.current
    if (!controller) return
    setLoadingMore(true)
    setMoreError(null)
    api
      .listJobMatches({ minScore, seen: false, cursor: nextCursor }, controller.signal)
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
  }, [nextCursor, loadingMore, loading, minScore])

  /** Takes the match out of the list and of the cached first page; returns what is needed to put it back. */
  const take = useCallback((id: JobMatch['id']) => {
    const index = itemsRef.current.findIndex((m) => m.id === id)
    if (index < 0) return null
    const item = itemsRef.current[index]
    const drop = (list: JobMatch[]) => list.filter((m) => m.id !== id)
    setItems(drop)
    const hit = recall<FirstPage>(shownKey.current)
    if (hit) remember(shownKey.current, { ...hit, items: drop(hit.items) })
    return { item, index }
  }, [])

  const putBack = useCallback(({ item, index }: { item: JobMatch; index: number }) => {
    const put = (list: JobMatch[]) =>
      list.some((m) => m.id === item.id) ? list : [...list.slice(0, index), item, ...list.slice(index)]
    setItems(put)
    const hit = recall<FirstPage>(shownKey.current)
    if (hit) remember(shownKey.current, { ...hit, items: put(hit.items) })
  }, [])

  /** Optimistic: gone at once, put back when the server refuses. Marking it seen is what keeps it from coming back. */
  const dismiss = useCallback(
    async (id: JobMatch['id']) => {
      const taken = take(id)
      if (!taken) return
      setNotice(null)
      setActionError(null)
      try {
        await api.markJobMatchSeen(id)
      } catch (err) {
        putBack(taken)
        setActionError(errorMessage(err, 'Could not remove the match.'))
      }
    },
    [take, putBack],
  )

  /** After a save the vacancy list owns the posting: drop the match. A failed "seen" only means it may show again. */
  const moveToVacancies = useCallback(
    (id: JobMatch['id']) => {
      const taken = take(id)
      if (!taken) return
      setActionError(null)
      setNotice(`Saved “${taken.item.job.title}” to your vacancies.`)
      api.markJobMatchSeen(id).catch(() => {})
    },
    [take],
  )

  const retry = useCallback(() => setAttempt((n) => n + 1), [])

  return {
    retry,
    items,
    hasMore: nextCursor !== null,
    loading,
    loadingMore,
    error,
    moreError,
    notice,
    actionError,
    loadMore,
    dismiss,
    moveToVacancies,
  }
}
