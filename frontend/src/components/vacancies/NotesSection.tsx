import { useState } from 'react'
import { Button } from '../ui/Button'
import { TextAreaField } from '../ui/Field'

const NOTES_MAX = 10_000

type NotesSectionProps = {
  savedNotes: string
  onSave: (notes: string) => Promise<boolean>
  busy: boolean
}

export function NotesSection({ savedNotes, onSave, busy }: NotesSectionProps) {
  const [notes, setNotes] = useState(savedNotes)
  const [confirmation, setConfirmation] = useState('')
  const unchanged = notes.trim() === savedNotes.trim()

  async function save() {
    if (busy || unchanged) return
    const trimmed = notes.trim()
    if (await onSave(trimmed)) {
      // Keep anything typed while the save was in flight.
      setNotes((current) => (current.trim() === trimmed ? trimmed : current))
      setConfirmation('Notes saved')
    }
  }

  return (
    <section className="dialog__section">
      <TextAreaField
        label="Notes"
        rows={4}
        value={notes}
        onChange={(e) => {
          setNotes(e.target.value)
          setConfirmation('')
        }}
        maxLength={NOTES_MAX}
      />
      <div className="dialog__notes-actions">
        <Button variant="outline" aria-disabled={busy || unchanged} onClick={save}>
          Save notes
        </Button>
        <p className="status-text dialog__confirmation" role="status">
          {confirmation}
        </p>
      </div>
    </section>
  )
}
