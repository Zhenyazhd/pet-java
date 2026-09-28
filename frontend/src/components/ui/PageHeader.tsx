import type { ReactNode } from 'react'

type PageHeaderProps = {
  eyebrow: string
  title: string
  lead: string
  actions?: ReactNode
}

export function PageHeader({ eyebrow, title, lead, actions }: PageHeaderProps) {
  return (
    <div className="page-header">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p className="page-lead">{lead}</p>
      </div>
      {actions ? <div className="header-actions">{actions}</div> : null}
    </div>
  )
}
