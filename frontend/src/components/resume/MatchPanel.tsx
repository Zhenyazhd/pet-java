import { useState } from 'react'
import type { MatchBreakdown, MatchPlatformScore, MatchResponse, MatchSuggestion } from '../../api/resumeTypes'
import { Banner } from '../ui/Banner'
import { Button } from '../ui/Button'

type MatchPanelProps = {
  /** False when the deployment has the vacancy match switched off. */
  available: boolean
  hasVacancy: boolean
  matching: boolean
  blocked: boolean
  report: MatchResponse | null
  error: string | null
  note: string | null
  onCheck: () => void
}

export function MatchPanel({ available, hasVacancy, matching, blocked, report, error, note, onCheck }: MatchPanelProps) {
  return (
    <section className="match-report" aria-labelledby="match-title">
      <div className="match-report__bar">
        <div>
          <h2 id="match-title" className="section-title">
            Vacancy match
          </h2>
          <p id="match-lead" className="muted-line">
            {!available
              ? 'Not available yet. Scoring your CV against a vacancy will come later.'
              : hasVacancy
                ? 'Builds the PDF of your saved CV and scores the text an ATS reads from it against the chosen vacancy on six ATS platforms. It takes a few minutes.'
                : 'Pick a vacancy above to check how your CV scores against it.'}
          </p>
        </div>
        <Button
          variant="outline"
          aria-describedby="match-lead"
          aria-disabled={!available || !hasVacancy || matching || blocked}
          onClick={onCheck}
        >
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
          <p className="match-report__average">
            <span className="match-report__score">{report.averageScore}</span>
            <span className="small-caps"> / 100 average</span>
          </p>
          {report.platforms.length > 0 && (
            <ul className="match-report__platforms">
              {report.platforms.map((platform) => (
                <PlatformRow key={platform.system} platform={platform} suggestions={report.suggestions} />
              ))}
            </ul>
          )}
          {report.suggestions.length > 0 && (
            <>
              <h3 className="small-caps">Suggestions</h3>
              <ul className="match-report__suggestions">
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

const DIMENSIONS: { key: keyof MatchBreakdown; title: string }[] = [
  { key: 'keywords', title: 'Keywords' },
  { key: 'experience', title: 'Experience' },
  { key: 'sections', title: 'Sections' },
  { key: 'formatting', title: 'Formatting' },
  { key: 'education', title: 'Education' },
]

type PlatformRowProps = { platform: MatchPlatformScore; suggestions: MatchSuggestion[] }

/** A platform's score as a button: it opens what works for this vacancy, what is missing, and what to change. */
function PlatformRow({ platform, suggestions }: PlatformRowProps) {
  const [open, setOpen] = useState(false)
  const panelId = `platform-${platform.system.replace(/\W+/g, '-')}`
  const name = platform.system.toLowerCase()
  const advice = suggestions.filter((suggestion) => suggestion.platforms.some((p) => p.toLowerCase() === name))
  const breakdown = platform.breakdown
  const dimensions = breakdown ? DIMENSIONS.filter(({ key }) => breakdown[key]) : []
  const hasDetails = dimensions.length > 0 || advice.length > 0

  return (
    <li>
      <div className="match-platform__row">
        <button
          type="button"
          className="link-button match-platform__toggle"
          aria-expanded={open}
          aria-controls={panelId}
          onClick={() => setOpen((o) => !o)}
        >
          {platform.system}
        </button>
        <span className="match-report__platform-score">{platform.overallScore}</span>
        <span className={platform.passesFilter ? 'badge badge--pass' : 'badge badge--fail'}>
          {platform.passesFilter ? 'Passes' : 'Fails'}
        </span>
      </div>
      {open && (
        <div id={panelId} className="match-platform__details">
          <p className="muted-line">
            {platform.overallScore >= 100
              ? 'Full marks on this platform.'
              : `${100 - platform.overallScore} points short of 100 on ${platform.system}, for this vacancy:`}
          </p>
          {!hasDetails && (
            <p className="muted-line">
              The model gave no details for {platform.system} in this check, so there is nothing more to show. Run
              Check match again to try for them.
            </p>
          )}
          {dimensions.map(({ key, title }) => {
            const dimension = breakdown?.[key]
            if (!dimension) return null
            return (
              <div key={key} className="match-dimension">
                <h4 className="small-caps">
                  {title}
                  {dimension.score !== null && ` · ${dimension.score}/100`}
                </h4>
                {dimension.good.length > 0 && (
                  <p>
                    <strong>Works:</strong> {dimension.good.join('; ')}
                  </p>
                )}
                {dimension.missing.length > 0 && (
                  <p>
                    <strong>Missing:</strong> {dimension.missing.join('; ')}
                  </p>
                )}
              </div>
            )
          })}
          {advice.length > 0 && (
            <div className="match-dimension">
              <h4 className="small-caps">What to change</h4>
              <ul>
                {advice.map((suggestion, index) => (
                  <li key={index}>
                    <span className="badge">{suggestion.impact} impact</span> {suggestion.summary}
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
            </div>
          )}
        </div>
      )}
    </li>
  )
}
