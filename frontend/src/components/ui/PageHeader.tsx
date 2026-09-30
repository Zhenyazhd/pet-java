import type { ReactNode } from 'react'

type PageHeaderProps = {
  eyebrow: string
  title: string
  lead?: string
  /** Extra content under the lead — e.g. a link back to the source posting. */
  children?: ReactNode
  actions?: ReactNode
}

export function PageHeader({ eyebrow, title, lead, children, actions }: PageHeaderProps) {
  return (
    <div className="page-header">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        {lead ? <p className="page-lead">{lead}</p> : null}
        {children}
      </div>
      {actions ? <div className="header-actions">{actions}</div> : null}
    </div>
  )
}
