import type { ApplicationStatus } from '../../api/types'

export function formatApplicationStatus(status: ApplicationStatus): string {
  const text = status.toLowerCase().replace(/_/g, ' ')
  return text.charAt(0).toUpperCase() + text.slice(1)
}

export function applicationStatusTone(status: ApplicationStatus): 'active' | 'closed' | null {
  if (status === 'INTERVIEW' || status === 'OFFER') return 'active'
  if (status === 'REJECTED' || status === 'WITHDRAWN') return 'closed'
  return null
}
