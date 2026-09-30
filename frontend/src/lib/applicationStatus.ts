import type { ApplicationStatus } from '../api/types'

/** `NOT_APPLIED` → `NOT APPLIED` for UI labels. */
export function formatApplicationStatus(status: string): string {
  return status.replace(/_/g, ' ')
}

/** Shared tone for status chips / row selects. */
export type ApplicationStatusTone = 'interview' | 'closed' | null

export function applicationStatusTone(status: string): ApplicationStatusTone {
  if (status === 'INTERVIEW') return 'interview'
  if (status === 'REJECTED' || status === 'WITHDRAWN') return 'closed'
  return null
}

function toneClass(prefix: string, status: ApplicationStatus | 'ALL'): string {
  if (status === 'ALL') return ''
  const tone = applicationStatusTone(status)
  return tone ? `${prefix}--${tone}` : ''
}

export function vacancyRowStatusClass(status: ApplicationStatus): string {
  return toneClass('vacancy-row__status', status)
}

export function statusFilterChipClass(status: ApplicationStatus | 'ALL'): string {
  return toneClass('status-filter__chip', status)
}
