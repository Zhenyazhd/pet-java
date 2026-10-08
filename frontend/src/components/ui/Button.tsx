import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { classNames } from '../../lib/classNames'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'outline' | 'ghost'
  block?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', block = false, className, type = 'button', ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={classNames('btn', `btn--${variant}`, block && 'btn--block', className)}
      {...rest}
    />
  )
})
