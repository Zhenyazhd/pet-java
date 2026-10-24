import { useEffect, useRef, type MouseEvent, type ReactNode } from 'react'

type DialogProps = {
  labelledBy: string
  onClose: () => void
  children: ReactNode
}

export function Dialog({ labelledBy, onClose, children }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose
  const pressedOnBackdrop = useRef(false)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    const handleClose = () => {
      if (!dialog.open) onCloseRef.current()
    }
    dialog.addEventListener('close', handleClose)
    dialog.showModal()
    return () => {
      dialog.removeEventListener('close', handleClose)
      dialog.close()
    }
  }, [])

  function onMouseDown(event: MouseEvent<HTMLDialogElement>) {
    pressedOnBackdrop.current = event.target === event.currentTarget
  }

  function onClick(event: MouseEvent<HTMLDialogElement>) {
    if (pressedOnBackdrop.current && event.target === event.currentTarget) onCloseRef.current()
  }

  return (
    // Clicking the backdrop is a mouse shortcut: the keyboard closes the dialog with Escape (native <dialog>).
    // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-noninteractive-element-interactions
    <dialog
      ref={ref}
      className="dialog"
      aria-labelledby={labelledBy}
      onMouseDown={onMouseDown}
      onClick={onClick}
    >
      <div className="dialog__body">{children}</div>
    </dialog>
  )
}
