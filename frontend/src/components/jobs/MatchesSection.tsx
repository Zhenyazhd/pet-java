import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useJobMatches } from '../../lib/useJobMatches'
import { Banner } from '../ui/Banner'
import { Button } from '../ui/Button'
import { MatchCard } from './MatchCard'

const DEBOUNCE_MS = 400

export function MatchesSection({ refreshKey }: { refreshKey: number }) {
  const [minScoreInput, setMinScoreInput] = useState('0')
  const [minScore, setMinScore] = useState(0)

  const scoreValid = /^\d{1,3}$/.test(minScoreInput) && Number(minScoreInput) <= 100

  useEffect(() => {
    if (!scoreValid) return
    const timer = window.setTimeout(() => setMinScore(Number(minScoreInput)), DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [minScoreInput, scoreValid])

  const matches = useJobMatches({ minScore }, refreshKey)

  return (
    <section aria-labelledby="matches-title" className="job-matches">
      <h2 id="matches-title" className="section-title">
        Matches
      </h2>
      <div className="match-filters">
        <div className="field match-filters__score">
          <label className="field__label small-caps" htmlFor="min-score-filter">
            Minimum score
          </label>
          <input
            id="min-score-filter"
            className="input input--score"
            type="number"
            inputMode="numeric"
            min={0}
            max={100}
            value={minScoreInput}
            aria-invalid={!scoreValid}
            aria-describedby={scoreValid ? undefined : 'min-score-error'}
            onChange={(e) => setMinScoreInput(e.target.value)}
          />
          {!scoreValid && (
            <p id="min-score-error" className="field__error">
              Enter a whole number from 0 to 100.
            </p>
          )}
        </div>
      </div>

      {/* Always rendered: a live region is only announced when its text changes. */}
      <p className="status-text" role="status">
        {matches.loading && matches.items.length > 0 ? 'Updating…' : ''}
      </p>
      <div role="status">
        {matches.notice && (
          <p className="field__hint">
            {matches.notice} <Link to="/vacancies">View vacancies</Link>
          </p>
        )}
      </div>
      {matches.actionError && <Banner tone="error">{matches.actionError}</Banner>}

      {matches.error ? (
        <div className="stack">
          <Banner tone="error">{matches.error}</Banner>
          <div>
            <Button variant="outline" onClick={matches.retry}>
              Try again
            </Button>
          </div>
        </div>
      ) : matches.loading && matches.items.length === 0 ? (
        <p className="status-text">Loading matches…</p>
      ) : matches.items.length === 0 && !matches.hasMore ? (
        <div className="empty">
          <h3>No matches</h3>
          <p>
            {minScore > 0
              ? 'Nothing at this score. Lower the minimum score, or run a search.'
              : 'Run a search to find postings that fit your profile.'}
          </p>
        </div>
      ) : (
        <>
          <ul className="match-list" aria-busy={matches.loading}>
            {matches.items.map((match) => (
              <MatchCard
                key={match.id}
                match={match}
                onRemove={matches.dismiss}
                onSaved={matches.moveToVacancies}
              />
            ))}
          </ul>
          {matches.moreError && <Banner tone="error">{matches.moreError}</Banner>}
          {/* Stays in place at the end, so keyboard focus is not dropped when the last page arrives. */}
          <Button
            variant="outline"
            aria-disabled={!matches.hasMore || matches.loadingMore}
            onClick={() => matches.hasMore && matches.loadMore()}
          >
            {matches.loadingMore ? 'Loading…' : matches.hasMore ? 'Load more' : 'No more matches'}
          </Button>
        </>
      )}
    </section>
  )
}
