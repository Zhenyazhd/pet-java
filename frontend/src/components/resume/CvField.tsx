import { useLayoutEffect, useRef } from 'react'

type CvFieldProps = {
  value: string
  onChange: (value: string) => void
  className?: string
  multiline?: boolean
}

/** Inline editable field on the CV sheet (auto-grows when multiline). */
export function CvField({ value, onChange, className, multiline }: CvFieldProps) {
  const ref = useRef<HTMLTextAreaElement>(null)

  useLayoutEffect(() => {
    if (!multiline || !ref.current) return
    const el = ref.current
    el.style.height = 'auto'
    el.style.height = `${el.scrollHeight}px`
  }, [multiline, value])

  if (multiline) {
    return (
      <textarea
        ref={ref}
        className={`cv-field cv-field--area ${className ?? ''}`}
        value={value}
        rows={1}
        onChange={(e) => onChange(e.target.value)}
        onClick={(e) => e.stopPropagation()}
      />
    )
  }

  return (
    <input
      className={`cv-field ${className ?? ''}`}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      onClick={(e) => e.stopPropagation()}
    />
  )
}
