type PageHeaderProps = {
  eyebrow: string
  title: string
  lead?: string
  titleId?: string
}

export function PageHeader({ eyebrow, title, lead, titleId }: PageHeaderProps) {
  return (
    <header className="page-header">
      <div className="page-header__text">
        <p className="small-caps page-header__eyebrow">{eyebrow}</p>
        <h1 id={titleId} className="page-header__title" tabIndex={-1}>{title}</h1>
        {lead && <p className="page-header__lead">{lead}</p>}
      </div>
    </header>
  )
}
