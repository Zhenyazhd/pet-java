import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { ApplicationStatus, Vacancy } from '../../api/types'
import { classNames } from '../../lib/classNames'
import { formatDate, safeHttpUrl } from '../../lib/format'
import { companyLabel, statusOf } from '../../lib/vacancies/vacancy'
import { Dialog } from '../Dialog'
import { Banner } from '../ui/Banner'
import { Button } from '../ui/Button'
import { DeleteFooter } from './DeleteFooter'
import { NotesSection } from './NotesSection'
import { StatusSelect } from './StatusSelect'

const TITLE_ID = 'vacancy-dialog-title'

type VacancyDialogProps = {
  vacancy: Vacancy
  onClose: () => void
  onStatusChange: (status: ApplicationStatus) => Promise<void>
  onNotesSave: (notes: string) => Promise<void>
  onDelete: () => Promise<void>
}

export function VacancyDialog({
  vacancy,
  onClose,
  onStatusChange,
  onNotesSave,
  onDelete,
}: VacancyDialogProps) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const href = safeHttpUrl(vacancy.url)
  const requirements = [...vacancy.requirements].sort((a, b) => Number(b.required) - Number(a.required))

  async function run(action: () => Promise<void>): Promise<boolean> {
    if (busy) return false
    setError(null)
    setBusy(true)
    try {
      await action()
      return true
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
      return false
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog labelledBy={TITLE_ID} onClose={onClose}>
      <header className="dialog__header">
        <div>
          <p className="small-caps dialog__eyebrow">{companyLabel(vacancy)}</p>
          <h2 id={TITLE_ID} className="dialog__title">
            {vacancy.title}
          </h2>
        </div>
        <Button variant="ghost" onClick={onClose}>
          Close
        </Button>
      </header>
      <p>
        <Link to={`/resume?vacancy=${vacancy.id}`}>Tailor my CV for this vacancy</Link>
      </p>

      <dl className="dialog__facts">
        <div>
          <dt className="small-caps">Status</dt>
          <dd>
            <StatusSelect
              value={statusOf(vacancy)}
              onChange={(status) => void run(() => onStatusChange(status))}
              label={`Status for ${vacancy.title}`}
              disabled={busy}
            />
          </dd>
        </div>
        <div>
          <dt className="small-caps">Added</dt>
          <dd>{formatDate(vacancy.createdAt)}</dd>
        </div>
        <div>
          <dt className="small-caps">Posting</dt>
          <dd>
            {href ? (
              <a href={href} target="_blank" rel="noreferrer noopener">
                Open original<span className="sr-only"> (opens in a new tab)</span>
              </a>
            ) : (
              <span className="dialog__plain-url">{vacancy.url}</span>
            )}
          </dd>
        </div>
      </dl>

      <section className="dialog__section" aria-labelledby="dialog-requirements">
        <h3 id="dialog-requirements" className="small-caps">
          Requirements
        </h3>
        {requirements.length === 0 ? (
          <p className="muted-line">None were found in the posting.</p>
        ) : (
          <ul className="tags">
            {requirements.map((item) => (
              <li key={item.id} className={classNames('tag', item.required && 'tag--required')}>
                {item.name}
                <span className="sr-only">{item.required ? ' (required)' : ' (optional)'}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      {vacancy.description && (
        <section className="dialog__section" aria-labelledby="dialog-description">
          <h3 id="dialog-description" className="small-caps">
            Description
          </h3>
          <p className="dialog__description">{vacancy.description}</p>
        </section>
      )}

      {vacancy.application && (
        <NotesSection
          savedNotes={vacancy.application.notes ?? ''}
          onSave={(notes) => run(() => onNotesSave(notes))}
          busy={busy}
        />
      )}

      {/* Next to the footer, where the failed save or delete was triggered. */}
      {error && <Banner tone="error">{error}</Banner>}

      <DeleteFooter onDelete={() => void run(onDelete)} busy={busy} />
    </Dialog>
  )
}
