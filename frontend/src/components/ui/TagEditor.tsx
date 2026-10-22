import type { KeyboardEvent, ReactNode, RefObject } from 'react'

type TagEditorProps = {
  /** Base id: the label is `${id}-label`, the message `${id}-msg` (for the inputs' aria-describedby). */
  id: string
  label: string
  /** Set when the add row is one input that the label names. */
  labelFor?: string
  counter: string
  hint: string
  tags: string[]
  /** The kind of tag, for "Remove role X". */
  noun: string
  disabled: boolean
  onRemove: (index: number) => void
  /** Where the focus goes once the removed tag's button is gone. */
  focusAfterRemove: RefObject<HTMLElement>
  message: string | null
  /** Announce the message at once (after a submit attempt). */
  alert: boolean
  /** The add row. */
  children: ReactNode
}

/** A labelled list of removable tags with an add row below it; the editors of roles and regions are built on it. */
export function TagEditor({
  id,
  label,
  labelFor,
  counter,
  hint,
  tags,
  noun,
  disabled,
  onRemove,
  focusAfterRemove,
  message,
  alert,
  children,
}: TagEditorProps) {
  return (
    <div className="field">
      <div className="field__row">
        {labelFor ? (
          <label id={`${id}-label`} className="field__label small-caps" htmlFor={labelFor}>
            {label}
          </label>
        ) : (
          <span id={`${id}-label`} className="field__label small-caps">
            {label}
          </span>
        )}
        <span className="field__counter small-caps">{counter}</span>
      </div>
      <p className="field__hint">{hint}</p>
      {tags.length > 0 && (
        <ul className="tag-list" aria-labelledby={`${id}-label`}>
          {tags.map((tag, index) => (
            <li key={tag} className="tag">
              <span className="tag__text">{tag}</span>
              <button
                type="button"
                className="tag__remove"
                aria-label={`Remove ${noun} ${tag}`}
                disabled={disabled}
                onClick={() => {
                  onRemove(index)
                  focusAfterRemove.current?.focus()
                }}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      {children}
      {message && (
        <p id={`${id}-msg`} className="field__error" role={alert ? 'alert' : undefined}>
          {message}
        </p>
      )}
    </div>
  )
}

/** Enter in an add-row input adds the tag instead of submitting the form. */
export function enterToAdd(add: () => void) {
  return (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key !== 'Enter') return
    event.preventDefault()
    add()
  }
}
