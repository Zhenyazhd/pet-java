import { Button } from '../ui/Button'

type SearchNowProps = { running: boolean; starting: boolean; busy: boolean; onStart: () => void }

export function SearchNow({ running, starting, busy, onStart }: SearchNowProps) {
  const blocker = running ? 'A run is in progress.' : null
  const disabled = blocker !== null || busy

  return (
    <div className="action">
      <Button
        block
        aria-disabled={disabled}
        aria-describedby={blocker ? 'search-help search-blocker' : 'search-help'}
        onClick={() => !disabled && onStart()}
      >
        {starting ? 'Starting…' : 'Search now'}
      </Button>
      <p id="search-help" className="field__hint">
        Looks for new postings on the job boards. Uses your search budget: it costs money and is limited per day.
      </p>
      {blocker && (
        <p id="search-blocker" className="status-text">
          {blocker}
        </p>
      )}
    </div>
  )
}
