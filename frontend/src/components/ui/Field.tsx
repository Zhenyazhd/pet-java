import { useId, type InputHTMLAttributes, type TextareaHTMLAttributes } from 'react'

type CommonProps = {
  label: string
  hint?: string
  /** Shown under the field and linked to it; `alert` announces it at once (after a submit attempt). */
  error?: string | null
  errorAlert?: boolean
}

function describedBy(id: string, hint?: string, error?: string | null): string | undefined {
  const ids = [hint ? `${id}-hint` : '', error ? `${id}-error` : ''].filter(Boolean)
  return ids.length > 0 ? ids.join(' ') : undefined
}

function FieldError({ id, error, alert }: { id: string; error?: string | null; alert?: boolean }) {
  if (!error) return null
  return (
    <p id={`${id}-error`} className="field__error" role={alert ? 'alert' : undefined}>
      {error}
    </p>
  )
}

export function Field({
  label,
  hint,
  error,
  errorAlert,
  ...input
}: CommonProps & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId()
  const hintId = `${id}-hint`
  return (
    <div className="field">
      <label className="field__label small-caps" htmlFor={id}>
        {label}
      </label>
      <input
        id={id}
        className="input"
        aria-describedby={describedBy(id, hint, error)}
        aria-invalid={error ? true : undefined}
        {...input}
      />
      {hint && (
        <p id={hintId} className="field__hint">
          {hint}
        </p>
      )}
      <FieldError id={id} error={error} alert={errorAlert} />
    </div>
  )
}

export function TextAreaField({
  label,
  hint,
  error,
  errorAlert,
  counter,
  ...textarea
}: CommonProps & { counter?: string } & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId()
  const hintId = `${id}-hint`
  return (
    <div className="field">
      <div className="field__row">
        <label className="field__label small-caps" htmlFor={id}>
          {label}
        </label>
        {counter && <span className="field__counter small-caps">{counter}</span>}
      </div>
      {hint && (
        <p id={hintId} className="field__hint">
          {hint}
        </p>
      )}
      <textarea
        id={id}
        className="input input--area"
        aria-describedby={describedBy(id, hint, error)}
        aria-invalid={error ? true : undefined}
        {...textarea}
      />
      <FieldError id={id} error={error} alert={errorAlert} />
    </div>
  )
}
