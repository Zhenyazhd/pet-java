import type { MatchResponse } from '../../types/resume'

type VacancyMatchPanelProps = {
  hasContext: boolean
  matching: boolean
  busy: boolean
  report: MatchResponse | null
  error: string | null
  onCheck: () => void
  onDismissError?: () => void
}

export function VacancyMatchPanel({
  hasContext,
  matching,
  busy,
  report,
  error,
  onCheck,
  onDismissError,
}: VacancyMatchPanelProps) {
  return (
    <div className="vacancy-match">
      <div className="vacancy-match__bar">
        <div className="vacancy-match__copy">
          <p className="vacancy-match__title">Vacancy match</p>
          <p className="vacancy-match__hint">
            {hasContext
              ? 'Score this resume against the attached vacancy via ATS Screener (6 platforms).'
              : 'Add vacancy context above to unlock a match check.'}
          </p>
        </div>
        <button
          type="button"
          className="button"
          onClick={onCheck}
          disabled={!hasContext || matching || busy}
        >
          {matching ? 'Checking…' : 'Check match'}
        </button>
      </div>

      {error && (
        <div className="vacancy-match__alert" role="alert">
          <div className="vacancy-match__alert-body">
            <p className="vacancy-match__alert-title">Match failed</p>
            <p className="vacancy-match__alert-text">{error}</p>
          </div>
          {onDismissError && (
            <button
              type="button"
              className="vacancy-match__alert-dismiss"
              onClick={onDismissError}
              aria-label="Dismiss match error"
            >
              Dismiss
            </button>
          )}
        </div>
      )}

      {report && !error && (
        <div className="vacancy-match__report" role="status">
          <div className="vacancy-match__score-row">
            <p className="vacancy-match__average">
              Average <strong>{report.averageScore}</strong>/100
            </p>
            {report.provider && (
              <p className="vacancy-match__meta">
                via {report.provider}
                {report.cached ? ' · cached' : ''}
              </p>
            )}
          </div>

          {report.platforms.length > 0 && (
            <ul className="vacancy-match__platforms">
              {report.platforms.map((p) => (
                <li key={p.system} className="vacancy-match__platform">
                  <span className="vacancy-match__platform-name">{p.system}</span>
                  <span className="vacancy-match__platform-score">{p.overallScore}</span>
                  <span
                    className={`vacancy-match__badge ${
                      p.passesFilter ? 'vacancy-match__badge--pass' : 'vacancy-match__badge--fail'
                    }`}
                  >
                    {p.passesFilter ? 'pass' : 'fail'}
                  </span>
                </li>
              ))}
            </ul>
          )}

          {report.suggestions.length > 0 && (
            <div className="vacancy-match__suggestions">
              <p className="vacancy-match__suggestions-title">Suggestions</p>
              <ul>
                {report.suggestions.map((s) => (
                  <li key={s.summary}>
                    <p className="vacancy-match__suggestion-summary">
                      <span className="vacancy-match__impact">{s.impact}</span> {s.summary}
                    </p>
                    {s.details.length > 0 && (
                      <ul className="vacancy-match__suggestion-details">
                        {s.details.map((d) => (
                          <li key={d}>{d}</li>
                        ))}
                      </ul>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
