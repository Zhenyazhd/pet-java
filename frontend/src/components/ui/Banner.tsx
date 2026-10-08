import type { ReactNode } from 'react'

export function Banner({ tone, children }: { tone: 'error' | 'ok'; children: ReactNode }) {
  return (
    <div className={`banner banner--${tone}`} role={tone === 'error' ? 'alert' : undefined}>
      {children}
    </div>
  )
}
