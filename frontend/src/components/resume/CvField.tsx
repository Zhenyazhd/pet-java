import { useLayoutEffect, useRef } from 'react'
import { classNames } from '../../lib/classNames'

type CvFieldProps = {
  value: string
  onChange: (value: string) => void
  label: string
  className?: string
  multiline?: boolean
  placeholder?: string
  maxLength?: number
  required?: boolean
}

/** The field grows with its text: no scrollbar, no hidden lines. */
function fitHeight(el: HTMLTextAreaElement) {
  el.style.height = 'auto'
  el.style.height = `${el.scrollHeight}px`
}

export function CvField({
  value,
  onChange,
  label,
  className,
  multiline,
  placeholder,
  maxLength,
  required,
}: CvFieldProps) {
  const ref = useRef<HTMLTextAreaElement>(null)

  useLayoutEffect(() => {
    if (ref.current) fitHeight(ref.current)
  }, [value])

  useLayoutEffect(() => {
    const el = ref.current
    const parent = el?.parentElement
    if (!el || !parent) return
    let width = parent.clientWidth
    let frame = 0
    let disposed = false
    const fit = () => {
      if (disposed) return
      cancelAnimationFrame(frame)
      frame = requestAnimationFrame(() => fitHeight(el))
    }
    const observer = new ResizeObserver(() => {
      if (parent.clientWidth === width) return
      width = parent.clientWidth
      fit()
    })
    observer.observe(parent)
    void document.fonts?.ready.then(fit)
    return () => {
      disposed = true
      cancelAnimationFrame(frame)
      observer.disconnect()
    }
  }, [])

  const shared = {
    value,
    'aria-label': label,
    placeholder,
    maxLength,
    'aria-required': required || undefined,
    'aria-invalid': required && !value.trim() ? true : undefined,
    className: classNames('cv-field', className),
  }

  return multiline ? (
    <textarea
      {...shared}
      ref={ref}
      rows={1}
      className={classNames(shared.className, 'cv-field--area')}
      onChange={(e) => onChange(e.target.value)}
    />
  ) : (
    <input {...shared} onChange={(e) => onChange(e.target.value)} />
  )
}
