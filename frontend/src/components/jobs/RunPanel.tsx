import type { JobRun } from '../../api/types'
import { formatUsd, formatWhen, isActive, isModelRateLimited, isRankOnly } from '../../lib/jobSearch'
import { Banner } from '../ui/Banner'

type RunPanelProps = {
  run: JobRun
  pollError: boolean
  pollStopped: boolean
}

export function RunPanel({ run, pollError, pollStopped }: RunPanelProps) {
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
      {pollStopped && active && (
        <p className="field__hint">Stopped checking; reload the page to see the latest status.</p>
      )}
      {pollError && active && !pollStopped && (
        <p className="field__error">Could not refresh the status. Still trying.</p>
      )}
      {run.error && <Banner tone="error">{run.error}</Banner>}
      {!active && isModelRateLimited(run) && (
        <p className="field__hint">The model provider limited the requests. Press Score unscored to score the rest.</p>
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

      {!rankOnly && run.sources.length > 0 && (
        <details className="run-details">
          <summary>Details per source</summary>
          <ul className="source-list" aria-label="Sources">
            {run.sources.map((source) => (
              <li key={source.source} className="source-row">
                <span className="source-row__name">{source.source}</span>
                <span className="badge">{source.status}</span>
                <span className="source-row__stats">
                  fetched {source.fetched} · new {source.newJobs} · dropped {source.dropped} ·{' '}
                  {formatUsd(source.costUsd)}
                </span>
                {source.error && <span className="source-row__error">{source.error}</span>}
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  )
}
