import { Outlet } from 'react-router-dom'
import type { JobSearchState } from '../api/types'
import { Banner } from '../components/ui/Banner'
import { Button } from '../components/ui/Button'
import { PageHeader } from '../components/ui/PageHeader'
import { toValues } from '../lib/jobSearch'
import { useDocumentTitle } from '../lib/useDocumentTitle'
import { useJobSearch, type JobSearch } from '../lib/useJobSearch'
import type { JobsContext } from '../lib/useJobsContext'
import { useProfileForm } from '../lib/useProfileForm'

// Owns the search state for both pages, so a running search keeps polling while the profile page is open.
export function JobsLayout() {
  const search = useJobSearch()
  const { data } = search

  if (data?.configured) return <JobsReady key={search.epoch} search={search} state={data} />
  return <JobsUnavailable search={search} />
}

// Its own component so the profile draft starts from the saved profile, known only after loading. The draft
// lives here to survive moving between the two pages; leaving through the top nav to another section still
// drops it, because BrowserRouter has no useBlocker.
function JobsReady({ search, state }: { search: JobSearch; state: JobSearchState }) {
  const form = useProfileForm(toValues(state.profile))
  return <Outlet context={{ search, state, form } satisfies JobsContext} />
}

function JobsUnavailable({ search }: { search: JobSearch }) {
  useDocumentTitle('Job matches')
  const { data } = search

  return (
    <section className="page" aria-labelledby="matches-page-title">
      <PageHeader eyebrow="Discovery" title="Job matches" titleId="matches-page-title" />
      <div role="status" className="status-text">
        {!data && !search.error && 'Loading…'}
      </div>
      {search.error ? (
        <div className="stack">
          <Banner tone="error">{search.error}</Banner>
          <div>
            <Button variant="outline" onClick={search.reload}>
              Try again
            </Button>
          </div>
        </div>
      ) : (
        data && (
          <section className="job-card" aria-labelledby="off-title">
            <h2 id="off-title" className="section-title">
              Job matches are switched off
            </h2>
            <p>
              Job matches are not switched on on this server. The server needs a JOB_LIST_TOKEN (the access token of the
              job-list service) in its configuration. Ask whoever runs this server to set it, then reload this page.
            </p>
          </section>
        )
      )}
    </section>
  )
}
