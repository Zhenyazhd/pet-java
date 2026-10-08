import { classNames } from '../../lib/classNames'

type PageHeaderProps = {
  eyebrow?: string
  title: string
  lead?: string
  titleId?: string
  /** One line of title and lead for pages whose content is the workspace, not the heading. */
  compact?: boolean
}

export function PageHeader({ eyebrow, title, lead, titleId, compact = false }: PageHeaderProps) {
  return (
    <header className={classNames('page-header', compact && 'page-header--compact')}>
      <div className="page-header__text">
        {eyebrow && <p className="small-caps page-header__eyebrow">{eyebrow}</p>}
        <h1 id={titleId} className="page-header__title" tabIndex={-1}>
          {title}
        </h1>
        {lead && <p className="page-header__lead">{lead}</p>}
      </div>
    </header>
  )
}
