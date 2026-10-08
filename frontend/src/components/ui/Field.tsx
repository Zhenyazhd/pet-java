import { useId, type InputHTMLAttributes, type TextareaHTMLAttributes } from 'react'

type CommonProps = {
  label: string
  hint?: string
}

export function Field({
  label,
  hint,
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
        aria-describedby={hint ? hintId : undefined}
        {...input}
      />
      {hint && (
        <p id={hintId} className="field__hint">
          {hint}
        </p>
      )}
    </div>
  )
}

export function TextAreaField({
  label,
  hint,
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
        aria-describedby={hint ? hintId : undefined}
        {...textarea}
      />
    </div>
  )
}
