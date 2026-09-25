export type ApplicationStatus =
  | 'NOT_APPLIED'
  | 'APPLIED'
  | 'INTERVIEW'
  | 'OFFER'
  | 'REJECTED'
  | 'WITHDRAWN'

export interface Requirement {
  id?: number
  name: string
  required: boolean
}

export interface ApplicationSummary {
  id: number
  status: ApplicationStatus
  applied: boolean
}

export interface Vacancy {
  id: number
  url: string
  title: string
  company: string | null
  description: string | null
  matchPercent: number | null
  requirements: Requirement[]
  application: ApplicationSummary | null
  createdAt: string
  updatedAt: string
}

export interface VacancyRequest {
  url: string
  title: string
  company?: string
  description?: string
  matchPercent?: number | null
  requirements: Requirement[]
}

export interface JobApplication {
  id: number
  vacancyId: number
  status: ApplicationStatus
  applied: boolean
  appliedAt: string | null
  notes: string | null
  createdAt: string
  updatedAt: string
}

export const APPLICATION_STATUSES: ApplicationStatus[] = [
  'NOT_APPLIED',
  'APPLIED',
  'INTERVIEW',
  'OFFER',
  'REJECTED',
  'WITHDRAWN',
]
