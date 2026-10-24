import type {
  AiScope,
  ChatTurn,
  MatchResponse,
  ReadabilityReport,
  ResumeDocument,
  SuggestResponse,
} from './resumeTypes'
import type {
  ApplicationStatus,
  AuthUser,
  Job,
  ApplicationSummary,
  JobMatchPage,
  JobMatchQuery,
  JobRun,
  JobSearchProfile,
  JobSearchProfileInput,
  JobSearchState,
  LoginRequest,
  Profile,
  PromptPart,
  RegisterRequest,
  Vacancy,
  VacancyCreateRequest,
  VacancyImportRequest,
  VacancyImportResult,
} from './types'

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

const NETWORK_ERROR = "Can't reach the server. Check your connection and try again."

/** fetch rejects with a bare TypeError ("Failed to fetch", "Load failed") when the network or the server is gone. */
async function safeFetch(path: string, init?: RequestInit): Promise<Response> {
  try {
    return await fetch(path, init)
  } catch (err) {
    // fetch rejects with a TypeError for a network failure; anything else (an abort, a bad argument) is not one.
    if (!(err instanceof TypeError)) throw err
    throw new ApiError(NETWORK_ERROR, 0)
  }
}

export function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}

/** A 409: the change clashes with the current state (a stale resume `version`, a duplicate vacancy). */
export function isConflict(error: unknown): boolean {
  return error instanceof ApiError && error.status === 409
}

type UnauthorizedListener = () => void

let unauthorizedListener: UnauthorizedListener | null = null

export function setUnauthorizedListener(listener: UnauthorizedListener | null): void {
  unauthorizedListener = listener
}

type RequestOptions = {
  skipAuthRedirect?: boolean
  signal?: AbortSignal
  responseType?: 'blob'
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

let csrfRefreshInFlight: Promise<void> | null = null

async function refreshCsrfCookie(): Promise<void> {
  if (!csrfRefreshInFlight) {
    csrfRefreshInFlight = safeFetch('/api/auth/csrf', { credentials: 'include' })
      .then(async (response) => {
        if (!response.ok) {
          throw new ApiError(await readErrorMessage(response), response.status)
        }
      })
      .finally(() => {
        csrfRefreshInFlight = null
      })
  }
  await csrfRefreshInFlight
}

async function ensureCsrfToken(): Promise<string> {
  const existing = readCookie('XSRF-TOKEN')
  if (existing) return existing
  await refreshCsrfCookie()
  const token = readCookie('XSRF-TOKEN')
  if (!token) throw new ApiError('CSRF token was not set', 403)
  return token
}

function needsCsrf(method: string): boolean {
  return !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method.toUpperCase())
}

async function readErrorMessage(response: Response): Promise<string> {
  const fallback = `Request failed (${response.status})`
  const text = await response.text()
  let data: unknown
  try {
    data = JSON.parse(text)
  } catch {
    // Not JSON (e.g. a proxy error page): show short plain text, never markup.
    return text && text.length <= 200 && !text.trimStart().startsWith('<') ? text : fallback
  }
  const { message, fields } = (data ?? {}) as { message?: unknown; fields?: unknown }
  const details =
    fields && typeof fields === 'object'
      ? Object.values(fields).filter((v): v is string => typeof v === 'string')
      : []
  if (details.length > 0) return details.join('. ')
  return typeof message === 'string' ? message : fallback
}

async function buildHeaders(method: string): Promise<Record<string, string>> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (needsCsrf(method)) {
    headers['X-XSRF-TOKEN'] = await ensureCsrfToken()
  }
  return headers
}

async function request<T>(path: string, init?: RequestInit, options?: RequestOptions): Promise<T> {
  const method = init?.method ?? 'GET'
  const send = async () =>
    safeFetch(path, {
      credentials: 'include',
      ...init,
      headers: await buildHeaders(method),
      signal: options?.signal,
    })

  let response = await send()

  if (response.status === 403 && needsCsrf(method)) {
    await refreshCsrfCookie()
    response = await send()
  }

  if (response.status === 204) return undefined as T

  if (!response.ok) {
    const message = await readErrorMessage(response)
    if (response.status === 401 && !options?.skipAuthRedirect) {
      unauthorizedListener?.()
    }
    throw new ApiError(message, response.status)
  }

  if (options?.responseType === 'blob') return (await response.blob()) as T

  const text = await response.text()
  if (!text) return null as T
  try {
    return JSON.parse(text) as T
  } catch {
    throw new ApiError(`Response was not valid JSON (${response.status})`, response.status)
  }
}

const json = (method: string, body: unknown): RequestInit => ({
  method,
  body: JSON.stringify(body),
})

export const api = {
  refreshCsrf: refreshCsrfCookie,

  getMe: () => request<AuthUser>('/api/auth/me', undefined, { skipAuthRedirect: true }),

  login: (body: LoginRequest) =>
    request<AuthUser>('/api/auth/login', json('POST', body), { skipAuthRedirect: true }),

  register: (body: RegisterRequest) =>
    request<AuthUser>('/api/auth/register', json('POST', body), { skipAuthRedirect: true }),

  logout: async () => {
    await request<void>('/api/auth/logout', { method: 'POST' }, { skipAuthRedirect: true })
    try {
      await refreshCsrfCookie()
    } catch {
      // The session is already closed. The next sign-in asks for a token again.
    }
  },

  getProfile: (signal?: AbortSignal) => request<Profile>('/api/profile', undefined, { signal }),

  saveProfile: (profile: Profile) => request<Profile>('/api/profile', json('PUT', profile)),

  getPromptParts: (signal?: AbortSignal) =>
    request<PromptPart[]>('/api/profile/prompts', undefined, { signal }),

  savePromptPart: (key: string, text: string) =>
    request<PromptPart>(`/api/profile/prompts/${encodeURIComponent(key)}`, json('PUT', { text })),

  publishPromptDefaults: () =>
    request<PromptPart[]>('/api/profile/prompts/publish', { method: 'POST' }),

  resetPromptPart: (key: string) =>
    request<void>(`/api/profile/prompts/${encodeURIComponent(key)}`, { method: 'DELETE' }),

  getResume: () => request<ResumeDocument>('/api/resume'),

  saveResume: (resume: ResumeDocument) => request<ResumeDocument>('/api/resume', json('PUT', resume)),

  startCompile: (signal?: AbortSignal) =>
    request<Job<null>>('/api/resume/compile', { method: 'POST' }, { signal }),

  getCompileJob: (jobId: string) => request<Job<null>>(`/api/resume/compile/${jobId}`),

  getCompiledPdf: (jobId: string, signal?: AbortSignal) =>
    request<Blob>(`/api/resume/compile/${jobId}/pdf`, undefined, { responseType: 'blob', signal }),

  suggestResumeSection: (
    body: {
      section: AiScope
      instruction: string
      itemIndex?: number
      history?: ChatTurn[]
      vacancyContext?: string
      model?: string
    },
    signal?: AbortSignal,
  ) => request<SuggestResponse>('/api/ai/resume/suggest', json('POST', body), { signal }),

  startReadability: (compileJobId: string, signal?: AbortSignal) =>
    request<Job<ReadabilityReport>>('/api/resume/readability', json('POST', { compileJobId }), { signal }),

  getReadabilityJob: (jobId: string) => request<Job<ReadabilityReport>>(`/api/resume/readability/${jobId}`),

  /** `compileJobId` is the finished PDF build: the match scores the text an ATS reads from that PDF. */
  startMatch: (vacancyContext: string, vacancyId: number | null, compileJobId: string, signal?: AbortSignal) =>
    request<Job<MatchResponse>>(
      '/api/ai/resume/match',
      json('POST', { vacancyContext, compileJobId, ...(vacancyId === null ? {} : { vacancyId }) }),
      { signal },
    ),

  getMatchJob: (jobId: string) => request<Job<MatchResponse>>(`/api/ai/resume/match/${jobId}`),

  listVacancies: (signal?: AbortSignal) => request<Vacancy[]>('/api/vacancies', undefined, { signal }),

  deleteVacancy: (id: number) => request<void>(`/api/vacancies/${id}`, { method: 'DELETE' }),

  startVacancyImport: (body: VacancyImportRequest) =>
    request<Job<VacancyImportResult>>('/api/vacancies/import', json('POST', body)),

  getVacancyImportJob: (jobId: string) =>
    request<Job<VacancyImportResult>>(`/api/vacancies/import/${jobId}`),

  createVacancy: (body: VacancyCreateRequest) =>
    request<Vacancy>('/api/vacancies', json('POST', body)),

  getJobSearch: (signal?: AbortSignal) =>
    request<JobSearchState>('/api/job-search', undefined, { signal }),

  saveJobSearchProfile: (body: JobSearchProfileInput) =>
    request<JobSearchProfile>('/api/job-search/profile', json('PUT', body)),

  startJobSearchRun: () => request<JobRun>('/api/job-search/runs', { method: 'POST' }),

  startJobRankRun: () => request<JobRun>('/api/job-search/runs/rank', { method: 'POST' }),

  listJobMatches: ({ minScore, seen, cursor }: JobMatchQuery, signal?: AbortSignal) => {
    const params = new URLSearchParams({
      minScore: String(minScore),
      delivered: String(seen),
      limit: '20',
    })
    if (cursor) params.set('cursor', cursor)
    return request<JobMatchPage>(`/api/job-search/matches?${params}`, undefined, { signal })
  },

  markJobMatchSeen: (id: number | string) =>
    request<void>(`/api/job-search/matches/${encodeURIComponent(id)}/seen`, { method: 'POST' }),

  updateApplication: (vacancyId: number, changes: { status: ApplicationStatus; notes?: string }) =>
    request<ApplicationSummary>(`/api/vacancies/${vacancyId}/application`, json('PUT', changes)),
}
