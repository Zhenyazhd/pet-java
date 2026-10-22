import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, isConflict } from '../../api/client'
import type { JobMatch } from '../../api/types'
import { errorMessage } from '../../lib/abort'
import { classNames } from '../../lib/classNames'
import { formatWhen, postingUrl, vacancyFromMatch } from '../../lib/jobSearch'
import { Button } from '../ui/Button'

const LONG_SNIPPET = 300
const REMOTE_LABEL: Record<string, string | undefined> = {
  REMOTE: 'Remote',
  HYBRID: 'Hybrid',
  ON_SITE: 'On site',
}

type MatchCardProps = {
  match: JobMatch
  saved: boolean
  onSeen: (id: JobMatch['id']) => Promise<void>
  onSaved: (id: JobMatch['id']) => void
}

export function MatchCard({ match, saved, onSeen, onSaved }: MatchCardProps) {
  const { job } = match
  const [expanded, setExpanded] = useState(false)
  const [saving, setSaving] = useState(false)
  const [note, setNote] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const link = postingUrl(job.url)
  const seen = match.deliveredAt !== null
  const posted = formatWhen(job.postedAt)
  const snippet = job.snippet?.trim() ?? ''
  const longSnippet = snippet.length > LONG_SNIPPET
  const facts = [
    job.company,
    job.location,
    REMOTE_LABEL[job.remote ?? 'UNKNOWN'] ?? null,
    job.contractType,
    job.salary,
  ].filter(Boolean)
  const skills = job.skills ?? []

  async function markSeen() {
    setError(null)
    try {
      await onSeen(match.id)
    } catch (err) {
      setError(errorMessage(err, 'Could not mark as seen.'))
    }
  }

  async function save() {
    if (!link || saving || saved) return
    setSaving(true)
    setError(null)
    try {
      await api.createVacancy(vacancyFromMatch(match, link))
      onSaved(match.id)
      setNote('Saved to your vacancies.')
    } catch (err) {
      if (isConflict(err)) {
        onSaved(match.id)
        setNote(errorMessage(err, 'This posting is already in your vacancies.'))
      } else {
        setError(errorMessage(err, 'Could not save the vacancy.'))
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <li className={classNames('match', seen && 'match--seen')}>
      <div className="match__head">
        <span className="badge match__score">
          <span aria-hidden="true">{match.score}</span>
          <span className="sr-only">Score {match.score} of 100</span>
        </span>
        <h3 className="match__title">{job.title}</h3>
      </div>
      {facts.length > 0 && <p className="match__facts">{facts.join(' · ')}</p>}
      <p className="match__meta small-caps">
        {job.source}
        {posted && ` · Posted ${posted}`}
        {seen && ' · Seen'}
      </p>
      {skills.length > 0 && (
        <ul className="skill-list" aria-label="Skills">
          {skills.map((skill, i) => (
            <li key={`${skill}-${i}`} className="skill">
              {skill}
            </li>
          ))}
        </ul>
      )}
      {match.reason && (
        <p className="match__reason">
          <strong>Why it fits: </strong>
          {match.reason}
        </p>
      )}
      {snippet && (
        <div>
          <p className={classNames('match__snippet', longSnippet && !expanded && 'match__snippet--clamped')}>
            {snippet}
          </p>
          {longSnippet && (
            <button
              type="button"
              className="link-button"
              aria-expanded={expanded}
              onClick={() => setExpanded((e) => !e)}
            >
              {expanded ? 'Show less' : 'Show more'}
            </button>
          )}
        </div>
      )}
      <div className="match__actions">
        {link && (
          <a className="btn btn--outline" href={link} target="_blank" rel="noopener noreferrer">
            Open posting<span className="sr-only"> (opens in a new tab)</span>
          </a>
        )}
        {/* Stays in place once seen, so a keyboard user does not lose the focus. */}
        <Button variant="ghost" aria-disabled={seen} onClick={() => !seen && markSeen()}>
          {seen ? 'Seen' : 'Mark as seen'}
          <span className="sr-only">: {job.title}</span>
        </Button>
        <Button
          variant="ghost"
          aria-disabled={!link || saving || saved}
          aria-describedby={link ? undefined : `${match.id}-nolink`}
          onClick={() => void save()}
        >
          {saved ? 'Saved' : saving ? 'Saving…' : 'Save to my vacancies'}
          <span className="sr-only">: {job.title}</span>
        </Button>
        {saved && (
          <Link className="btn btn--ghost" to="/vacancies">
            View vacancies
          </Link>
        )}
      </div>
      {!link && (
        <p id={`${match.id}-nolink`} className="field__hint">
          This posting has no usable link, so it cannot be saved.
        </p>
      )}
      <p className="status-text" role="status">
        {note}
      </p>
      {error && (
        <p className="field__error" role="alert">
          {error}
        </p>
      )}
    </li>
  )
}
