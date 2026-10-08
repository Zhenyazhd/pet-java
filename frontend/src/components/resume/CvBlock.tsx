import type { ReactNode } from 'react'
import { classNames } from '../../lib/classNames'
import { FocusButton } from './FocusButton'

type CvBlockProps = {
  title?: string
  target: string
  active: boolean
  onFocus: () => void
  children: ReactNode
}

export function CvBlock({ title, target, active, onFocus, children }: CvBlockProps) {
  return (
    <section className={classNames('cv-block', active && 'cv-block--active')}>
      <div className="cv-block__head">
        {title ? <h2 className="cv-block__title">{title}</h2> : <span />}
        <FocusButton target={target} active={active} onFocus={onFocus} />
      </div>
      {children}
    </section>
  )
}
