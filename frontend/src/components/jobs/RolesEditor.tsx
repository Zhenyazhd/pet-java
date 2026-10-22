import { useId, useRef } from 'react'
import { ROLES_MAX, ROLE_LENGTH_MAX } from '../../lib/jobSearch'
import { Button } from '../ui/Button'
import { TagEditor, enterToAdd } from '../ui/TagEditor'

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

  return (
    <TagEditor
      id={id}
      label="Roles"
      labelFor={`${id}-input`}
      counter={`${roles.length} / ${ROLES_MAX}`}
      hint="Job titles you are looking for, for example “Backend engineer”. Put the most important role first: LinkedIn and Welcome to the Jungle search only the first role; HelloWork searches all of them."
      tags={roles}
      noun="role"
      disabled={disabled}
      onRemove={(index) => {
        onRemove(index)
        onTouch()
      }}
      focusAfterRemove={input}
      message={message}
      alert={alert}
    >
      <div className="add-row">
        <input
          id={`${id}-input`}
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
          onKeyDown={enterToAdd(onAdd)}
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
    </TagEditor>
  )
}
