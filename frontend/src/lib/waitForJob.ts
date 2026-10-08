import type { Job } from '../api/types'

const POLL_DELAYS_MS = [500, 1000, 2000]
const MAX_CONSECUTIVE_POLL_FAILURES = 2


export async function waitForJob<T>(
  job: Job<T>,
  getJob: (jobId: string) => Promise<Job<T>>,
  options: { maxWaitMs: number; tooLongMessage: string; signal?: AbortSignal },
): Promise<Job<T>> {
  const startedAt = Date.now()
  let current = job
  let failures = 0
  for (let poll = 0; current.status === 'QUEUED' || current.status === 'RUNNING'; poll++) {
    if (Date.now() - startedAt > options.maxWaitMs) throw new Error(options.tooLongMessage)
    await sleep(POLL_DELAYS_MS[Math.min(poll, POLL_DELAYS_MS.length - 1)], options.signal)
    try {
      current = await getJob(current.id)
      failures = 0
    } catch (err) {
      if (++failures > MAX_CONSECUTIVE_POLL_FAILURES) throw err
    }
  }
  return current
}

function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(new DOMException('Aborted', 'AbortError'))
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort)
      resolve()
    }, ms)
    const onAbort = () => {
      clearTimeout(timer)
      reject(new DOMException('Aborted', 'AbortError'))
    }
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}
