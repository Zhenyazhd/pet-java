import { api } from '../api/client'
import type { CompileJob } from '../api/types'

const POLL_DELAYS_MS = [500, 1000, 2000]
// Shorter than the server's 5-minute queue limit: nobody waits that long on a button. The job may
// still finish on the server afterwards, and the next click then gets the PDF from the cache.
const MAX_WAIT_MS = 2 * 60 * 1000

/**
 * Queues a PDF build of the saved resume, polls until it finishes and returns the PDF.
 * The server compiles in the background, so this can take a few seconds when the queue is busy.
 */
export async function compileResume(): Promise<Blob> {
  const startedAt = Date.now()
  let job = await api.startCompile()
  for (let poll = 0; isActive(job); poll++) {
    if (Date.now() - startedAt > MAX_WAIT_MS) {
      throw new Error('PDF build is taking too long. Try again in a minute.')
    }
    await sleep(POLL_DELAYS_MS[Math.min(poll, POLL_DELAYS_MS.length - 1)])
    job = await api.getCompileJob(job.id)
  }
  if (job.status === 'FAILED') {
    const detail = job.error?.message ?? 'PDF build failed'
    throw new Error(job.error?.code === 'compile_error' ? `LaTeX error: ${detail}` : detail)
  }
  return api.getCompiledPdf(job.id)
}

function isActive(job: CompileJob): boolean {
  return job.status === 'QUEUED' || job.status === 'RUNNING'
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
