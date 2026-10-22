import { useOutletContext } from 'react-router-dom'
import type { JobSearchState } from '../api/types'
import type { JobSearch } from './useJobSearch'
import type { useProfileForm } from './useProfileForm'

export type JobsContext = { search: JobSearch; state: JobSearchState; form: ReturnType<typeof useProfileForm> }

/** What JobsLayout shares with its pages: the one search state and the profile draft. */
export function useJobsContext() {
  return useOutletContext<JobsContext>()
}
