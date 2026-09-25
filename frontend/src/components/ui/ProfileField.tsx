import type { InputHTMLAttributes, ReactNode, TextareaHTMLAttributes } from 'react'

type CommonProps = {
  label: string
  children?: ReactNode
}

type ProfileInputProps = CommonProps &
  Omit<InputHTMLAttributes<HTMLInputElement>, 'className'> & {
    multiline?: false
  }

type ProfileTextareaProps = CommonProps &
  Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'className'> & {
    multiline: true
  }

export function ProfileField(props: ProfileInputProps | ProfileTextareaProps) {
  const { label, multiline, ...rest } = props

  return (
    <label className="profile-field">
      <span className="profile-field__label">{label}</span>
      {multiline ? (
        <textarea className="profile-field__input" {...(rest as TextareaHTMLAttributes<HTMLTextAreaElement>)} />
      ) : (
        <input className="profile-field__input" {...(rest as InputHTMLAttributes<HTMLInputElement>)} />
      )}
    </label>
  )
}
