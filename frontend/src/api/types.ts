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
  company?: string | null
  description?: string | null
  matchPercent?: number | null
  requirements: Array<Pick<Requirement, 'name' | 'required'>>
}

export interface VacancyImportRequest {
  url: string
  pastedText: string
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

export type AuthUser = {
  id: number
  email: string
  displayName: string
  role?: 'USER' | 'ADMIN'
}

export type LoginRequest = {
  email: string
  password: string
}

export type RegisterRequest = {
  email: string
  password: string
  displayName: string
  inviteCode: string
}
