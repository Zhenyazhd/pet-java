import { useEffect, useId, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../../api/client'
import { Button } from '../ui/Button'

type CreateCvCardProps = {
  creating: boolean
  replacesEdits: boolean
  hold: string | null
  failure: string | null
  onCreate: () => void
  onCancel: () => void
}

export function CreateCvCard({
  creating,
  replacesEdits,
  hold,
  failure,
  onCreate,
  onCancel,
}: CreateCvCardProps) {
  const statusId = useId()
  const createRef = useRef<HTMLButtonElement>(null)
  const [hasCareerPath, setHasCareerPath] = useState<boolean | null>(null)

  useEffect(() => {
    let cancelled = false
    api
      .getProfile()
      .then((profile) => !cancelled && setHasCareerPath(profile.careerPath.trim().length > 0))
      .catch(() => !cancelled && setHasCareerPath(null))
    return () => {
      cancelled = true
    }
  }, [])

  const missing = hasCareerPath === false
  const unavailable = creating || hold !== null || missing

  return (
    <section className="create-cv" aria-labelledby="create-cv-title">
      <h2 id="create-cv-title" className="section-title">
        Start from your profile
      </h2>
      <p>
        You have not saved a CV here yet, so the sheet below is a template. The AI can write your CV from the
        career path in your profile and put it on the sheet. You review it, edit what you like and save.
        {replacesEdits &&
          ' It saves what is on the sheet now, then replaces it; Undo brings that back until your next edit.'}
      </p>
      {missing && (
        <p id={`${statusId}-missing`} className="muted-line">
          Your profile has no career path yet. <Link to="/profile">Fill in your profile</Link> first: the AI only
          uses what you wrote there.
        </p>
      )}
      <div className="create-cv__actions">
        <Button
          ref={createRef}
          aria-disabled={unavailable}
          aria-describedby={missing ? `${statusId}-missing` : statusId}
          onClick={() => !unavailable && onCreate()}
        >
          {creating ? 'Creating…' : replacesEdits ? 'Create CV again' : 'Create CV'}
        </Button>
        {creating && (
          <Button
            variant="ghost"
            onClick={() => {
              onCancel()
              // Cancel disappears with the request: the keyboard goes back to the button it came from.
              createRef.current?.focus()
            }}
          >
            Cancel
          </Button>
        )}
        <p id={statusId} className="status-text muted-line" role="status">
          {creating ? 'Writing your CV from your profile. This can take a minute.' : (failure ?? hold ?? '')}
        </p>
      </div>
    </section>
  )
}
