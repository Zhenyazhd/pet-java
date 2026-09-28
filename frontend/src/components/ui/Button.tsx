import type { ButtonHTMLAttributes, ReactNode } from 'react'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'ghost'
  active?: boolean
  children: ReactNode
}

export function Button({
  variant = 'primary',
  active = false,
  className,
  type = 'button',
  children,
  ...props
}: ButtonProps) {
  const classes = [
    'button',
    variant === 'ghost' ? 'button--ghost' : '',
    active ? 'button--active' : '',
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <button type={type} className={classes} {...props}>
      {children}
    </button>
  )
}
