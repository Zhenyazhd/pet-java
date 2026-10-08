import type { MatchResponse } from '../../api/resumeTypes'
import { Banner } from '../ui/Banner'
import { Button } from '../ui/Button'

type MatchPanelProps = {
  hasVacancy: boolean
  matching: boolean
  blocked: boolean
  report: MatchResponse | null
  error: string | null
  note: string | null
  onCheck: () => void
}

export function MatchPanel({ hasVacancy, matching, blocked, report, error, note, onCheck }: MatchPanelProps) {
  return (
    <section className="match" aria-labelledby="match-title">
      <div className="match__bar">
        <div>
          <h2 id="match-title" className="section-title">
            Vacancy match
          </h2>
          <p className="muted-line">
            {hasVacancy
              ? 'Scores your saved CV against the chosen vacancy on six ATS platforms. It takes a few minutes.'
              : 'Pick a vacancy above to check how your CV scores against it.'}
          </p>
        </div>
        <Button variant="outline" aria-disabled={!hasVacancy || matching || blocked} onClick={onCheck}>
          {matching ? 'Checking…' : 'Check match'}
        </Button>
      </div>

      <p className="status-text muted-line" role="status">
        {matching ? 'Scoring your CV. This can take several minutes.' : ''}
      </p>

      {error && <Banner tone="error">{error}</Banner>}
      {report && note && <p className="status-text muted-line">{note}</p>}

      {report && (
        <div>
          <p className="match__average">
            <span className="match__score">{report.averageScore}</span>
            <span className="small-caps"> / 100 average</span>
          </p>
          {report.platforms.length > 0 && (
            <ul className="match__platforms">
              {report.platforms.map((platform) => (
                <li key={platform.system}>
                  <span>{platform.system}</span>
                  <span className="match__platform-score">{platform.overallScore}</span>
                  <span className={platform.passesFilter ? 'badge badge--pass' : 'badge badge--fail'}>
                    {platform.passesFilter ? 'Passes' : 'Fails'}
                  </span>
                </li>
              ))}
            </ul>
          )}
          {report.suggestions.length > 0 && (
            <>
              <h3 className="small-caps">Suggestions</h3>
              <ul className="match__suggestions">
                {report.suggestions.map((suggestion, index) => (
                  <li key={index}>
                    <p>
                      <span className="badge">{suggestion.impact} impact</span> {suggestion.summary}
                    </p>
                    {suggestion.details.length > 0 && (
                      <ul>
                        {suggestion.details.map((detail, i) => (
                          <li key={i}>{detail}</li>
                        ))}
                      </ul>
                    )}
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
    </section>
  )
}
