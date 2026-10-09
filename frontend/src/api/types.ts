export type AuthUser = {
  id: number
  email: string
  displayName: string
  /** What this deployment offers; a switched-off feature is hidden or disabled instead of failing on click. */
  features?: { atsMatch: boolean }
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

export type Profile = {
  displayName: string
  email: string
  careerPath: string
}

export const APPLICATION_STATUSES = [
  'NOT_APPLIED',
  'APPLIED',
  'INTERVIEW',
  'OFFER',
  'REJECTED',
  'WITHDRAWN',
] as const

export type ApplicationStatus = (typeof APPLICATION_STATUSES)[number]

export type Requirement = {
  id: number
  name: string
  required: boolean
}

export type ApplicationSummary = {
  id: number
  status: ApplicationStatus
  notes: string | null
}

export type Vacancy = {
  id: number
  url: string
  title: string
  company: string | null
  description: string | null
  requirements: Requirement[]
  /** Null only for a vacancy that has never had a status set. */
  application: ApplicationSummary | null
  createdAt: string
}

export type JobApplication = {
  id: number
  status: ApplicationStatus
  notes: string | null
}

export type VacancyImportRequest = {
  url: string
  pastedText: string
}

export type JobStatus = 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED'

/** A background job; `result` is the handler's output once the job is DONE. */
export type Job<TResult> = {
  id: string
  status: JobStatus
  error: { code: string; message: string } | null
  result: TResult | null
  createdAt: string
  finishedAt: string | null
}

export type VacancyImportResult = { vacancyId: number }
