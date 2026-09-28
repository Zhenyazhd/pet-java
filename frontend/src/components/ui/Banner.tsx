import type { ReactNode } from 'react'

type BannerProps = {
  tone: 'error' | 'ok'
  children: ReactNode
  pre?: boolean
}

export function Banner({ tone, children, pre = false }: BannerProps) {
  const className = `banner banner--${tone}${tone === 'error' ? ' latex-error' : ''}`
  if (pre || tone === 'error') {
    return <pre className={className}>{children}</pre>
  }
  return <div className={className}>{children}</div>
}
