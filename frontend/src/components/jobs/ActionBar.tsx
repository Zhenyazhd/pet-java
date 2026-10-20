import type { JobSearchState } from '../../api/types'
import { classNames } from '../../lib/classNames'
import { runSummary, unrankedLabel } from '../../lib/jobSearch'
import type { JobSearch } from '../../lib/useJobSearch'
import { Banner } from '../ui/Banner'
import { ContinueRanking } from './ContinueRanking'
import { RunPanel } from './RunPanel'
import { SearchNow } from './SearchNow'

type ActionBarProps = { search: JobSearch; state: JobSearchState }

export function ActionBar({ search, state }: ActionBarProps) {
  const { latestRun, unranked } = state
  const busy = search.starting || search.startingRank
  const starting = search.starting ? 'Starting the search…' : search.startingRank ? 'Starting the scoring…' : null

  return (
    <section className="job-card" aria-labelledby="actions-title">
      <h2 id="actions-title" className="section-title">
        Run
      </h2>
      <div className="actions">
        <SearchNow running={search.active} starting={search.starting} busy={busy} onStart={search.startRun} />
        <ContinueRanking
          unranked={unranked}
          running={search.active}
          starting={search.startingRank}
          busy={busy}
          onStart={search.startRankRun}
        />
      </div>
      {search.runError && <Banner tone="error">{search.runError}</Banner>}
      {search.rankError && <Banner tone="error">{search.rankError}</Banner>}
      {unranked !== null && unranked > 0 && <p className="muted-line">{unrankedLabel(unranked)}</p>}
      {unranked === 0 && latestRun !== null && !search.active && (
        <p className="muted-line">All postings found so far are scored</p>
      )}
      {/* Always in the page, so a screen reader announces text that appears in it. */}
      <div role="status" className="run-status">
        {starting && <p className="status-text">{starting}</p>}
        {latestRun && (
          <p className={classNames('status-text', 'run-summary', `run-summary--${latestRun.status.toLowerCase()}`)}>
            {runSummary(latestRun, unranked)}
          </p>
        )}
      </div>
      {latestRun && <RunPanel run={latestRun} profile={state.profile} pollError={search.pollError} pollStopped={search.pollStopped} />}
    </section>
  )
}
