import type { ApplicationStatus, Vacancy } from '../../api/types'

export function statusOf(vacancy: Vacancy): ApplicationStatus {
  return vacancy.application?.status ?? 'NOT_APPLIED'
}

export function companyLabel(vacancy: Vacancy): string {
  return vacancy.company ?? 'Company not set'
}
