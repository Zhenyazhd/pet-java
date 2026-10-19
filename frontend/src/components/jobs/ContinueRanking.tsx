import { Button } from '../ui/Button'

type ContinueRankingProps = {
  unranked: number | null
  running: boolean
  starting: boolean
  busy: boolean
  onStart: () => void
}

const UNKNOWN = 'Could not check how many postings are unscored right now.'

export function ContinueRanking({ unranked, running, starting, busy, onStart }: ContinueRankingProps) {
  const blocker = running
    ? 'A run is in progress.'
    : unranked === null
      ? UNKNOWN
      : unranked <= 0
        ? 'Nothing left to score.'
        : null
  const disabled = blocker !== null || busy
  const label = unranked !== null && unranked > 0 ? `Score unscored (${unranked})` : 'Score unscored postings'

  return (
    <div className="action">
      <Button
        block
        variant="outline"
        aria-disabled={disabled}
        aria-describedby={blocker ? 'rank-help rank-blocker' : 'rank-help'}
        onClick={() => !disabled && onStart()}
      >
        {starting ? 'Starting…' : label}
      </Button>
      <p id="rank-help" className="field__hint">
        Scores postings already found with the model. Does not search the job boards again.
      </p>
      {blocker && (
        <p id="rank-blocker" className="status-text">
          {blocker}
        </p>
      )}
    </div>
  )
}
