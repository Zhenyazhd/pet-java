import { useId, useRef, type KeyboardEvent } from 'react'
import { ROLES_MAX, ROLE_LENGTH_MAX } from '../../lib/jobSearch'
import { Button } from '../ui/Button'

type RolesEditorProps = {
  roles: string[]
  draft: string
  onDraft: (value: string) => void
  onAdd: () => void
  onRemove: (index: number) => void
  error: string | null
  note: string | null
  alert: boolean
  onTouch: () => void
  disabled: boolean
}

export function RolesEditor({
  roles,
  draft,
  onDraft,
  onAdd,
  onRemove,
  error,
  note,
  alert,
  onTouch,
  disabled,
}: RolesEditorProps) {
  const id = useId()
  const input = useRef<HTMLInputElement>(null)
  const message = note ?? error

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Enter') return
    event.preventDefault()
    onAdd()
  }

  return (
    <div className="field">
      <div className="field__row">
        <label className="field__label small-caps" htmlFor={id}>
          Roles
        </label>
        <span className="field__counter small-caps">
          {roles.length} / {ROLES_MAX}
        </span>
      </div>
      <p className="field__hint">
        Job titles you are looking for, for example “Backend engineer”. Put the most important role first: LinkedIn and
        Welcome to the Jungle search only the first role; HelloWork searches all of them.
      </p>
      {roles.length > 0 && (
        <ul className="tag-list" aria-label="Roles to search for">
          {roles.map((role, index) => (
            <li key={role} className="tag">
              <span className="tag__text">{role}</span>
              <button
                type="button"
                className="tag__remove"
                aria-label={`Remove role ${role}`}
                disabled={disabled}
                onClick={() => {
                  onRemove(index)
                  onTouch()
                  // The removed tag's button is gone; keep the focus in the editor.
                  input.current?.focus()
                }}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="add-row">
        <input
          id={id}
          ref={input}
          className="input"
          value={draft}
          maxLength={ROLE_LENGTH_MAX}
          autoComplete="off"
          aria-describedby={message ? `${id}-msg` : undefined}
          aria-invalid={error !== null}
          readOnly={disabled}
          onBlur={onTouch}
          onChange={(e) => onDraft(e.target.value)}
          onKeyDown={onKeyDown}
        />
        <Button
          variant="outline"
          aria-disabled={disabled || !draft.trim()}
          onClick={() => {
            if (disabled) return
            onAdd()
            onTouch()
          }}
        >
          Add
        </Button>
      </div>
      {message && (
        <p id={`${id}-msg`} className="field__error" role={alert ? 'alert' : undefined}>
          {message}
        </p>
      )}
    </div>
  )
}
