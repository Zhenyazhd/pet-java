import type {
  ApplicationStatus,
  AuthUser,
  CompileJob,
  JobApplication,
  LoginRequest,
  RegisterRequest,
  Vacancy,
  VacancyImportRequest,
  VacancyRequest,
} from './types'
import type {
  ChatTurn,
  ResumeDocument,
  AiScope,
  SuggestResponse,
  MatchResponse,
} from '../types/resume'

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}

/** CSRF rejection (invalid/missing token) — Spring Security returns 403. */
export function isForbidden(error: unknown): boolean {
  return error instanceof ApiError && error.status === 403
}

/** Optimistic-concurrency rejection (e.g. a stale resume save) — server returns 409. */
export function isConflict(error: unknown): boolean {
  return error instanceof ApiError && error.status === 409
}

type UnauthorizedListener = () => void

let unauthorizedListener: UnauthorizedListener | null = null

/** Register a handler for session expiry (401 on protected calls). */
export function setUnauthorizedListener(listener: UnauthorizedListener | null): void {
  unauthorizedListener = listener
}

type RequestOptions = {
  /** Do not notify global 401 handler (e.g. GET /api/auth/me while bootstrapping). */
  skipAuthRedirect?: boolean
  /** Default JSON; use `blob` for binary endpoints (compiled PDF). */
  responseType?: 'json' | 'blob'
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name.replace(/[$()*+.?[\\\]^{|}]/g, '\\$&')}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

function readXsrfToken(): string | null {
  return readCookie('XSRF-TOKEN')
}

/** Shared in-flight CSRF mint so parallel mutations don't fan out N GETs. */
let csrfRefreshInFlight: Promise<void> | null = null

async function refreshCsrfCookie(): Promise<void> {
  if (!csrfRefreshInFlight) {
    csrfRefreshInFlight = fetch('/api/auth/csrf', { credentials: 'include' })
      .then(() => undefined)
      .finally(() => {
        csrfRefreshInFlight = null
      })
  }
  await csrfRefreshInFlight
}

/** Return the current XSRF token, minting a cookie first only if one is missing. After logout, call {@link refreshCsrfCookie} instead. */
async function ensureCsrfCookie(): Promise<string | null> {
  const existing = readXsrfToken()
  if (existing) return existing
  await refreshCsrfCookie()
  return readXsrfToken()
}

function needsCsrf(method: string): boolean {
  const m = method.toUpperCase()
  return m !== 'GET' && m !== 'HEAD' && m !== 'OPTIONS' && m !== 'TRACE'
}

async function readErrorMessage(response: Response): Promise<string> {
  const text = await response.text()
  let message = `Request failed (${response.status})`
  try {
    const data = JSON.parse(text)
    if (typeof data?.message === 'string') {
      message = data.message
    }
  } catch {
    if (text) message = text
  }
  return message
}

async function buildRequestHeaders(
  method: string,
  init: RequestInit | undefined,
  options: RequestOptions | undefined,
): Promise<Record<string, string>> {
  const headers: Record<string, string> = {
    ...(options?.responseType === 'blob' ? {} : { 'Content-Type': 'application/json' }),
    ...(init?.headers as Record<string, string> | undefined),
  }
  if (needsCsrf(method)) {
    const token = await ensureCsrfCookie()
    if (token) {
      headers['X-XSRF-TOKEN'] = token
    }
  }
  return headers
}

async function request<T>(path: string, init?: RequestInit, options?: RequestOptions): Promise<T> {
  const method = init?.method ?? 'GET'

  let response = await fetch(path, {
    credentials: 'include',
    ...init,
    headers: await buildRequestHeaders(method, init, options),
  })

  // Stale/missing CSRF token: any mutation, not just logout, can hit this if the
  // XSRF cookie expired or was cleared mid-session. Mint a fresh one and retry once.
  if (response.status === 403 && needsCsrf(method)) {
    await refreshCsrfCookie()
    response = await fetch(path, {
      credentials: 'include',
      ...init,
      headers: await buildRequestHeaders(method, init, options),
    })
  }

  if (response.status === 204) {
    return undefined as T
  }

  if (!response.ok) {
    const message = await readErrorMessage(response)
    if (response.status === 401 && !options?.skipAuthRedirect) {
      unauthorizedListener?.()
    }
    throw new ApiError(message, response.status)
  }

  if (options?.responseType === 'blob') {
    return (await response.blob()) as T
  }

  const text = await response.text()
  return (text ? JSON.parse(text) : null) as T
}

export type Profile = {
  displayName: string
  email: string
  careerPath: string
}

export const api = {
  ensureCsrf: () => ensureCsrfCookie(),
  /** Force a new XSRF-TOKEN (required after logout — session invalidate leaves a stale cookie). */
  refreshCsrf: () => refreshCsrfCookie(),

  getMe: () => request<AuthUser>('/api/auth/me', undefined, { skipAuthRedirect: true }),

  login: (body: LoginRequest) =>
    request<AuthUser>(
      '/api/auth/login',
      {
        method: 'POST',
        body: JSON.stringify(body),
      },
      { skipAuthRedirect: true },
    ),

  register: (body: RegisterRequest) =>
    request<AuthUser>(
      '/api/auth/register',
      {
        method: 'POST',
        body: JSON.stringify(body),
      },
      { skipAuthRedirect: true },
    ),

  // POST logout is permitAll but still CSRF-protected — send X-XSRF-TOKEN.
  // After success, refresh CSRF for the next anonymous session.
  logout: async () => {
    await request<void>(
      '/api/auth/logout',
      {
        method: 'POST',
      },
      { skipAuthRedirect: true },
    )
    await refreshCsrfCookie()
  },

  listVacancies: () => request<Vacancy[]>('/api/vacancies'),

  getVacancy: (id: number) => request<Vacancy>(`/api/vacancies/${id}`),

  createVacancy: (body: VacancyRequest) =>
    request<Vacancy>('/api/vacancies', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  importVacancy: (body: VacancyImportRequest) =>
    request<Vacancy>('/api/vacancies/import', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  updateVacancy: (id: number, body: VacancyRequest) =>
    request<Vacancy>(`/api/vacancies/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),

  deleteVacancy: (id: number) =>
    request<void>(`/api/vacancies/${id}`, {
      method: 'DELETE',
    }),

  createApplication: (vacancyId: number, status: ApplicationStatus, notes?: string) =>
    request<JobApplication>('/api/applications', {
      method: 'POST',
      body: JSON.stringify({ vacancyId, status, notes: notes || null }),
    }),

  /** Omit `notes` to leave existing notes unchanged (status-only update). */
  updateApplication: (id: number, status: ApplicationStatus, notes?: string) =>
    request<JobApplication>(`/api/applications/${id}`, {
      method: 'PUT',
      body: JSON.stringify(notes === undefined ? { status } : { status, notes }),
    }),

  getResume: () => request<ResumeDocument>('/api/resume'),

  saveResume: (resume: ResumeDocument) =>
    request<ResumeDocument>('/api/resume', {
      method: 'PUT',
      body: JSON.stringify(resume),
    }),

  startCompile: () => request<CompileJob>('/api/resume/compile', { method: 'POST' }),

  getCompileJob: (jobId: string) => request<CompileJob>(`/api/resume/compile/${jobId}`),

  getCompiledPdf: (jobId: string) =>
    request<Blob>(`/api/resume/compile/${jobId}/pdf`, undefined, { responseType: 'blob' }),

  suggestResumeSection: (
    section: AiScope,
    instruction: string,
    itemIndex?: number,
    history?: ChatTurn[],
    vacancyContext?: string,
    model?: string,
  ) =>
    request<SuggestResponse>('/api/ai/resume/suggest', {
      method: 'POST',
      body: JSON.stringify({
        section,
        instruction,
        ...(itemIndex === undefined ? {} : { itemIndex }),
        ...(history && history.length > 0 ? { history } : {}),
        ...(vacancyContext?.trim() ? { vacancyContext: vacancyContext.trim() } : {}),
        ...(model?.trim() ? { model: model.trim() } : {}),
      }),
    }),

  matchResume: (vacancyContext: string, vacancyId?: number) =>
    request<MatchResponse>('/api/ai/resume/match', {
      method: 'POST',
      body: JSON.stringify({
        vacancyContext: vacancyContext.trim(),
        ...(vacancyId != null ? { vacancyId } : {}),
      }),
    }),

  getProfile: () => request<Profile>('/api/profile'),

  saveProfile: (profile: Profile) =>
    request<Profile>('/api/profile', {
      method: 'PUT',
      body: JSON.stringify(profile),
    }),
}
