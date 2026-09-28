import type { ReactNode } from 'react'

type CvSectionProps = {
  title: string
  active: boolean
  onSelect: (e?: { stopPropagation(): void }) => void
  children: ReactNode
}

/** Selectable resume section block (Profil, Education, …). */
export function CvSection({ title, active, onSelect, children }: CvSectionProps) {
  return (
    <div
      role="button"
      tabIndex={0}
      className={`cv-block cv-section ${active ? 'cv-block--active' : ''}`}
      onClick={(e) => onSelect(e)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') onSelect()
      }}
    >
      <h2 className="cv-section__title">{title}</h2>
      <div className="cv-section__body">{children}</div>
    </div>
  )
}
