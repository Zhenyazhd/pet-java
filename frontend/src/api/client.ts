import type {
  ApplicationStatus,
  JobApplication,
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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
    ...init,
  })

  if (response.status === 204) {
    return undefined as T
  }

  if (!response.ok) {
    throw new Error(await readErrorMessage(response))
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

  updateApplication: (id: number, status: ApplicationStatus, notes?: string) =>
    request<JobApplication>(`/api/applications/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ status, notes: notes || null }),
    }),

  getResume: () => request<ResumeDocument>('/api/resume'),

  saveResume: (resume: ResumeDocument) =>
    request<ResumeDocument>('/api/resume', {
      method: 'PUT',
      body: JSON.stringify(resume),
    }),

  compileResume: async (): Promise<Blob> => {
    const response = await fetch('/api/resume/compile', { method: 'POST' })
    if (!response.ok) throw new Error(await readErrorMessage(response))
    return response.blob()
  },

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

  matchResume: (vacancyContext: string) =>
    request<MatchResponse>('/api/ai/resume/match', {
      method: 'POST',
      body: JSON.stringify({ vacancyContext: vacancyContext.trim() }),
    }),

  getProfile: () => request<Profile>('/api/profile'),

  saveProfile: (profile: Profile) =>
    request<Profile>('/api/profile', {
      method: 'PUT',
      body: JSON.stringify(profile),
    }),
}
