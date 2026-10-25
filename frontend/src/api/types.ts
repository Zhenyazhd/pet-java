export type AuthUser = {
  id: number
  email: string
  displayName: string
  /** 'ADMIN' sees the AI instructions on the Profile page; anyone else does not. */
  role?: string
  /** What this deployment offers; a switched-off feature is hidden or disabled instead of failing on click. */
  features?: { atsMatch: boolean; pdfReadability?: boolean }
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

export type PromptPart = {
  key: string
  title: string
  description: string
  defaultText: string
  customText: string | null
  customized: boolean
  defaultChanged: boolean
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
  /** Sent for every vacancy; null only while an unsaved change is being rolled back. */
  application: ApplicationSummary | null
  createdAt: string
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

export type VacancyCreateRequest = {
  url: string
  title: string
  company: string | null
  description: string | null
  matchPercent: number
}

export type JobRegion = { country: string; city: string | null }

export type JobSearchProfile = {
  id: number | string
  name: string
  expectations: string
  roles: string[]
  regions: JobRegion[]
  // Boards the profile uses, and boards switched on at the server; absent in answers of an older server.
  sources?: string[]
  enabledSources?: string[]
  minScore: number
}

export type JobSearchProfileInput = Omit<JobSearchProfile, 'id' | 'name' | 'sources' | 'enabledSources'>

export type JobRunStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'PARTIAL' | 'FAILED'

export type JobRunSource = {
  source: string
  status: string
  fetched: number
  newJobs: number
  dropped: number
  costUsd: number
  error: string | null
}

export type JobRun = {
  id: number
  status: JobRunStatus
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
  fetched: number
  newJobs: number
  ranked: number
  costUsd: number
  error: string | null
  sources: JobRunSource[]
  // Absent in answers of an older server: that is a full run.
  mode?: 'FULL' | 'RANK_ONLY'
}

export type JobSearchState = {
  configured: boolean
  profile: JobSearchProfile | null
  latestRun: JobRun | null
  // null: the server could not count them
  unranked: number | null
}

export type JobRemote = 'REMOTE' | 'HYBRID' | 'ON_SITE' | 'UNKNOWN'

export type JobPosting = {
  id: number
  source: string
  title: string
  company: string | null
  location: string | null
  remote: JobRemote | null
  contractType: string | null
  salary: string | null
  url: string | null
  postedAt: string | null
  skills: string[]
  snippet: string | null
}

export type JobMatch = {
  id: number
  score: number
  reason: string | null
  rankedAt: string
  deliveredAt: string | null
  job: JobPosting
}

export type JobMatchPage = { items: JobMatch[]; nextCursor: string | null }

export type JobMatchSort = 'score' | 'posted'

export type JobMatchQuery = {
  minScore: number
  seen: boolean
  /** Best score first (the default) or newest posting first. */
  sort?: JobMatchSort
  cursor?: string | null
}
