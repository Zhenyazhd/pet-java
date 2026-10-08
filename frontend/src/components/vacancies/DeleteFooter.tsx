import { useEffect, useRef, useState } from 'react'
import { Button } from '../ui/Button'

type DeleteFooterProps = {
  onDelete: () => void
  busy: boolean
}

export function DeleteFooter({ onDelete, busy }: DeleteFooterProps) {
  const [confirming, setConfirming] = useState(false)
  const startRef = useRef<HTMLButtonElement>(null)
  const keepRef = useRef<HTMLButtonElement>(null)
  const wasConfirming = useRef(false)

  useEffect(() => {
    if (confirming) keepRef.current?.focus()
    else if (wasConfirming.current) startRef.current?.focus()
    wasConfirming.current = confirming
  }, [confirming])

  return (
    <footer className="dialog__footer">
      {confirming ? (
        <>
          <span>Delete this vacancy for good?</span>
          <Button ref={keepRef} variant="ghost" onClick={() => setConfirming(false)}>
            Keep it
          </Button>
          <Button variant="outline" aria-disabled={busy} onClick={() => !busy && onDelete()}>
            Yes, delete
          </Button>
        </>
      ) : (
        <Button ref={startRef} variant="ghost" onClick={() => setConfirming(true)}>
          Delete vacancy
        </Button>
      )}
    </footer>
  )
}
