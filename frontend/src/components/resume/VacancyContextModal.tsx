import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import { Button } from '../ui/Button'

type VacancyContextModalProps = {
  open: boolean
  draft: string
  onDraftChange: (value: string) => void
  onClose: () => void
  onClear: () => void
  onSave: () => void
}

export function VacancyContextModal({
  open,
  draft,
  onDraftChange,
  onClose,
  onClear,
  onSave,
}: VacancyContextModalProps) {
  useEffect(() => {
    if (!open) return

    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }

    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, onClose])

  if (!open) return null

  return createPortal(
    <div
      className="modal-backdrop"
      role="presentation"
      onClick={(e) => {
        // Close only when the dimmed overlay itself is clicked, not the dialog.
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div className="context-modal" role="dialog" aria-modal="true" aria-labelledby="vacancy-context-title">
        <div className="context-modal__bar">
          <strong id="vacancy-context-title">Vacancy context</strong>
          <Button variant="ghost" onClick={onClose}>
            Close
          </Button>
        </div>
        <div className="context-modal__body">
          <p className="context-modal__lead">
            Paste the job description here. It will be sent with every AI message on this page so
            suggestions can target that role. Changing context clears the chat and any unsaved resume
            draft (reloads the last saved resume from the server).
          </p>
          <textarea
            className="context-modal__textarea"
            value={draft}
            onChange={(e) => onDraftChange(e.target.value)}
            placeholder="Paste vacancy title, requirements, responsibilities…"
            rows={14}
            autoFocus
          />
          <div className="context-modal__actions">
            <Button variant="ghost" onClick={onClear}>
              Clear
            </Button>
            <Button onClick={onSave}>Save context</Button>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  )
}
