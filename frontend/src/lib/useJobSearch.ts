import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ApiError } from '../api/client'
import type { JobSearchProfileInput, JobSearchState } from '../api/types'
import { errorMessage, isAbortError } from './abort'
import { isActive } from './jobSearch'
import { recall, remember } from './pageCache'

const POLL_MS = 4_000
const SLOW_POLL_MS = 10_000
const SLOW_AFTER_FAILURES = 3
const GIVE_UP_MS = 15 * 60 * 1000

function startError(err: unknown, noun: 'search' | 'scoring'): string {
  if (err instanceof ApiError && err.status === 429) {
    return `Daily ${noun === 'search' ? 'search ' : ''}limit reached. ${err.message}`
  }
  if (err instanceof ApiError && err.status === 503) {
    return `The ${noun} service is unavailable right now. Try again later.`
  }
  // A 409 carries the server's own wording ("Nothing to rank").
  return errorMessage(err, `The ${noun} could not be started.`)
}

/** A poll brings the run, never the profile (the form owns it); a null counter keeps the last known one. */
function mergePoll(prev: JobSearchState, next: JobSearchState): JobSearchState {
  return { ...next, profile: prev.profile, unranked: next.unranked ?? prev.unranked }
}

const CACHE_KEY = 'jobSearch'

/** The search profile and the latest run; polls the run only while it is queued or running. */
export function useJobSearch() {
  const [data, setData] = useState<JobSearchState | null>(() => recall<JobSearchState>(CACHE_KEY) ?? null)
  const [error, setError] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const [runError, setRunError] = useState<string | null>(null)
  const [rankError, setRankError] = useState<string | null>(null)
  const [startingRank, setStartingRank] = useState(false)
  const [pollError, setPollError] = useState(false)
  const [pollStopped, setPollStopped] = useState(false)
  // Bumped when a run leaves QUEUED/RUNNING, so the matches list knows to reload.
  const [finishedCount, setFinishedCount] = useState(0)
  const wasActive = useRef(false)
  // `data` may be a cached copy; settled once the server has answered, and the epoch moves when that
  // answer brings a different profile, so the form can start over from it.
  const [settled, setSettled] = useState(false)
  const [epoch, setEpoch] = useState(0)
  const dataRef = useRef(data)
  dataRef.current = data
  const startedMeanwhile = useRef(false)

  const load = useCallback(() => {
    const abort = new AbortController()
    setError(null)
    startedMeanwhile.current = false
    api
      .getJobSearch(abort.signal)
      .then((next) => {
        const prev = dataRef.current
        if (prev && JSON.stringify(prev.profile) !== JSON.stringify(next.profile)) setEpoch((n) => n + 1)
        // A run started while this was in flight is newer than this answer.
        setData((cur) => (cur && startedMeanwhile.current ? { ...next, latestRun: cur.latestRun } : next))
        setSettled(true)
      })
      .catch((err) => {
        if (isAbortError(err)) return
        setError(errorMessage(err, 'Failed to load job search'))
        setSettled(true)
      })
    return () => abort.abort()
  }, [])

  useEffect(() => load(), [load])

  useEffect(() => {
    if (data) remember(CACHE_KEY, data)
  }, [data])

  // Only the counter: a stale answer must not replace the run a start just returned.
  const refreshCounter = useCallback(() => {
    api
      .getJobSearch()
      .then((next) => setData((prev) => prev && { ...prev, unranked: next.unranked ?? prev.unranked }))
      .catch(() => {})
  }, [])

  const status = data?.latestRun?.status
  const active = isActive(status)

  useEffect(() => {
    if (wasActive.current && !active) {
      setFinishedCount((n) => n + 1)
      setRunError(null)
      setRankError(null)
    }
    wasActive.current = active
  }, [active])

  useEffect(() => {
    if (!active) return
    let startedAt = Date.now()
    let timer: number | undefined
    let abort: AbortController | null = null
    let failures = 0
    setPollStopped(false)

    const schedule = () => {
      timer = window.setTimeout(tick, failures >= SLOW_AFTER_FAILURES ? SLOW_POLL_MS : POLL_MS)
    }
    function tick() {
      // A hidden tab stops asking; coming back resumes (see onVisible).
      if (document.hidden) return
      if (Date.now() - startedAt > GIVE_UP_MS) return setPollStopped(true)
      abort = new AbortController()
      const mine = abort
      api
        .getJobSearch(mine.signal)
        .then((next) => {
          failures = 0
          setPollError(false)
          // The form owns the profile while the page is open; a poll only brings the run.
          setData((prev) => prev && mergePoll(prev, next))
          schedule()
        })
        .catch((err) => {
          if (isAbortError(err)) return
          failures += 1
          setPollError(true)
          schedule()
        })
    }
    function onVisible() {
      if (document.hidden) return
      // The time away does not count towards giving up: only time spent looking at the page does.
      startedAt = Date.now()
      window.clearTimeout(timer)
      abort?.abort()
      tick()
    }

    schedule()
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      window.clearTimeout(timer)
      abort?.abort()
      document.removeEventListener('visibilitychange', onVisible)
      setPollError(false)
    }
  }, [active])

  const saveProfile = useCallback(async (input: JobSearchProfileInput) => {
    const profile = await api.saveJobSearchProfile(input)
    setData((prev) => (prev ? { ...prev, profile } : prev))
    return profile
  }, [])

  const clearErrors = useCallback(() => {
    setRunError(null)
    setRankError(null)
  }, [])

  /** True when a run was started; a failure is left in runError. */
  const startRun = useCallback(async () => {
    setStarting(true)
    setRunError(null)
    setRankError(null)
    try {
      const latestRun = await api.startJobSearchRun()
      startedMeanwhile.current = true
      setData((prev) => (prev ? { ...prev, latestRun } : prev))
      return true
    } catch (err) {
      setRunError(startError(err, 'search'))
      return false
    } finally {
      setStarting(false)
    }
  }, [])

  const startRankRun = useCallback(async () => {
    setStartingRank(true)
    setRankError(null)
    setRunError(null)
    try {
      const latestRun = await api.startJobRankRun()
      startedMeanwhile.current = true
      setData((prev) => (prev ? { ...prev, latestRun } : prev))
      if (latestRun.mode !== 'RANK_ONLY') {
        setRankError('A search is already running. Scoring was not started; try again when it finishes.')
      }
    } catch (err) {
      setRankError(startError(err, 'scoring'))
      if (err instanceof ApiError && err.status === 409) refreshCounter()
    } finally {
      setStartingRank(false)
    }
  }, [refreshCounter])

  return {
    data,
    settled,
    epoch,
    error,
    reload: load,
    active,
    starting,
    runError,
    pollError,
    pollStopped,
    finishedCount,
    rankError,
    startingRank,
    saveProfile,
    startRun,
    startRankRun,
    clearErrors,
  }
}

export type JobSearch = ReturnType<typeof useJobSearch>
