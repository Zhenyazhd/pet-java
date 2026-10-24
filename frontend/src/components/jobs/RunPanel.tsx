import type { JobRun, JobSearchProfile } from '../../api/types'
import { formatUsd, formatWhen, isActive, isModelRateLimited, isRankOnly } from '../../lib/jobSearch'
import { Banner } from '../ui/Banner'

type RunPanelProps = {
  run: JobRun
  profile: JobSearchProfile | null
  pollError: boolean
  pollStopped: boolean
}

// Every board the service knows; one that is switched off or not in the profile has no row in the run.
const SOURCES = ['LINKEDIN', 'HELLOWORK', 'GREENHOUSE', 'WTTJ']

// Why a board has no row in this run: off at the server, left out of the profile (saved before it was switched on), or still to come.
function idleLabel(name: string, profile: JobSearchProfile | null, runActive: boolean) {
  if (profile?.enabledSources && !profile.enabledSources.includes(name)) return 'disabled on server'
  if (profile?.sources && !profile.sources.includes(name)) return 'not in profile: save the profile'
  return runActive ? 'waiting' : 'not in this run'
}

// A source left RUNNING in a run that is over was cut short.
const sourceLabel = (status: string, runActive: boolean) =>
  status === 'RUNNING' ? (runActive ? 'fetching now…' : 'interrupted') : status.toLowerCase()

export function RunPanel({ run, profile, pollError, pollStopped }: RunPanelProps) {
  const active = isActive(run.status)
  const rankOnly = isRankOnly(run)
  const when = formatWhen(run.finishedAt ?? run.startedAt ?? run.createdAt, true)

  return (
    <section className="run-panel" aria-labelledby="run-title" aria-busy={active}>
      <h3 id="run-title" className="run-panel__title small-caps">
        {rankOnly ? 'Latest scoring' : 'Latest search'}
      </h3>
      {when && (
        <p className="muted-line">
          {active ? 'Started' : 'Finished'} {when}
        </p>
      )}
      {/* Always in the page, so a screen reader announces the polling messages that appear in it. */}
      <div role="status">
        {pollStopped && active && (
          <p className="field__hint">Stopped checking; reload the page to see the latest status.</p>
        )}
        {pollError && active && !pollStopped && (
          <p className="field__error">Could not refresh the status. Still trying.</p>
        )}
      </div>
      {run.error && <Banner tone="error">{run.error}</Banner>}
      {!active && isModelRateLimited(run) && (
        <p className="field__hint">The model provider limited the requests. Press Score unscored to score the rest.</p>
      )}

      {!rankOnly && (
        <ul className="source-list" aria-label="Sources">
          {SOURCES.map((name) => {
            const source = run.sources.find((row) => row.source === name)
            if (!source) {
              return (
                <li key={name} className="source-row source-row--inactive">
                  <span className="source-row__name">{name}</span>
                  <span className="badge">{idleLabel(name, profile, active)}</span>
                </li>
              )
            }
            return (
              <li key={name} className="source-row">
                <span className="source-row__name">{name}</span>
                <span className="badge">{sourceLabel(source.status, active)}</span>
                {source.status !== 'RUNNING' && (
                  <span className="source-row__stats">
                    fetched {source.fetched} · new {source.newJobs} · dropped {source.dropped} ·{' '}
                    {formatUsd(source.costUsd)}
                  </span>
                )}
                {source.error && <span className="source-row__error">{source.error}</span>}
              </li>
            )
          })}
        </ul>
      )}

      <dl className="totals">
        {!rankOnly && (
          <>
            <div>
              <dt className="small-caps">Fetched</dt>
              <dd>{run.fetched}</dd>
            </div>
            <div>
              <dt className="small-caps">New</dt>
              <dd>{run.newJobs}</dd>
            </div>
          </>
        )}
        <div>
          <dt className="small-caps">{rankOnly ? 'Scored' : 'Ranked'}</dt>
          <dd>{run.ranked}</dd>
        </div>
        <div>
          <dt className="small-caps">Cost</dt>
          <dd>{formatUsd(run.costUsd)}</dd>
        </div>
      </dl>

    </section>
  )
}
