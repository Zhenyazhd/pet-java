import { useState } from 'react'
import type { ReadabilityReport } from '../../api/resumeTypes'
import { Banner } from '../ui/Banner'
import { Button } from '../ui/Button'

type ReadabilityPanelProps = {
  /** False when the deployment has the ATS worker switched off. */
  available: boolean
  checking: boolean
  blocked: boolean
  report: ReadabilityReport | null
  error: string | null
  note: string | null
  onCheck: () => void
}

const VERDICT = {
  ok: 'An ATS can read your PDF.',
  warnings: 'An ATS can read your PDF, but some things could trip it up.',
  issues: 'An ATS may fail to read parts of your PDF.',
} as const

const CONTACTS = [
  ['email', 'Email'],
  ['phone', 'Phone'],
  ['url', 'Profile link'],
] as const

export function ReadabilityPanel({ available, checking, blocked, report, error, note, onCheck }: ReadabilityPanelProps) {
  // The lines an ATS lost belong to every text problem; show them once, under the first.
  const firstTextIssue = report?.issues.findIndex((issue) => issue.code.startsWith('text_'))
  return (
    <section className="match-report" aria-labelledby="readability-title">
      <div className="match-report__bar">
        <div>
          <h2 id="readability-title" className="section-title">
            ATS readability
          </h2>
          <p id="readability-lead" className="muted-line">
            {available
              ? 'Builds the PDF of your saved CV and checks that an ATS can read it: text, headings, contacts and reading order.'
              : 'Not available yet.'}
          </p>
        </div>
        <Button
          variant="outline"
          aria-describedby="readability-lead"
          aria-disabled={!available || checking || blocked}
          onClick={onCheck}
        >
          {checking ? 'Checking…' : 'Check PDF'}
        </Button>
      </div>

      <p className="status-text muted-line" role="status">
        {checking ? 'Building the PDF and checking it. This takes a few seconds.' : ''}
      </p>

      {error && <Banner tone="error">{error}</Banner>}
      {report && note && <p className="status-text muted-line">{note}</p>}

      {report && (
        <div>
          <p className="match-report__average">
            <span className="match-report__score">{report.score}</span>
            <span className="small-caps"> / 100</span>
          </p>
          <p className="muted-line">{VERDICT[report.verdict]}</p>

          {report.issues.length > 0 && (
            <ul className="match-report__suggestions">
              {report.issues.map((issue, index) => (
                <li key={`${issue.code}-${index}`}>
                  <p>
                    <span className="badge">{issue.severity}</span> {issue.message}
                  </p>
                  {index === firstTextIssue && report.lostLines.length > 0 && (
                    <ul>
                      {report.lostLines.map((line, i) => (
                        <li key={i}>{line}</li>
                      ))}
                    </ul>
                  )}
                </li>
              ))}
            </ul>
          )}

          <p className="muted-line">
            Sections found: {report.sectionsFound.length > 0 ? report.sectionsFound.join(', ') : 'none'}.{' '}
            {CONTACTS.map(([key, label]) => `${label}: ${report.contacts[key] ? 'readable' : 'not found'}`).join(' · ')}.
          </p>

          <AtsText text={report.text} truncated={report.textTruncated} />
        </div>
      )}
    </section>
  )
}

/** The text an ATS extracted, folded behind a link-style toggle (the same look as "Show more" on a job match). */
function AtsText({ text, truncated }: { text: string; truncated: boolean }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="readability__text">
      <button type="button" className="link-button" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        {open ? 'Hide what an ATS reads from your PDF' : 'Show what an ATS reads from your PDF'}
      </button>
      {open && (
        <pre tabIndex={0} role="region" aria-label="Text an ATS reads from your PDF">
          {text}
        </pre>
      )}
      {open && truncated && <p className="muted-line">Shortened.</p>}
    </div>
  )
}
