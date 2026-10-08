import { api } from '../../api/client'
import type { VacancyImportRequest } from '../../api/types'
import { waitForJob } from '../waitForJob'

const MAX_WAIT_MS = 3 * 60 * 1000

export async function importVacancy(
  body: VacancyImportRequest,
  signal?: AbortSignal,
): Promise<number> {
  const job = await waitForJob(await api.startVacancyImport(body), api.getVacancyImportJob, {
    maxWaitMs: MAX_WAIT_MS,
    tooLongMessage: 'Reading the posting is taking too long. Check the list again in a minute.',
    signal,
  })
  if (job.status === 'FAILED' || !job.result) {
    throw new Error(job.error?.message ?? 'The vacancy could not be saved.')
  }
  return job.result.vacancyId
}
