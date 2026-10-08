type FocusButtonProps = {
  target: string
  active: boolean
  onFocus: () => void
}

export function FocusButton({ target, active, onFocus }: FocusButtonProps) {
  return (
    <button
      type="button"
      className="focus-btn small-caps"
      aria-pressed={active}
      aria-label={`Ask AI about ${target}`}
      onClick={onFocus}
    >
      Ask AI
    </button>
  )
}
