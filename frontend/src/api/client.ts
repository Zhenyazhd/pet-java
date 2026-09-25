import type { ApplicationStatus, JobApplication, Vacancy, VacancyRequest } from './types'

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

  const text = await response.text()
  const data = text ? JSON.parse(text) : null

  if (!response.ok) {
    const message =
      typeof data?.message === 'string' ? data.message : `Request failed (${response.status})`
    throw new Error(message)
  }

  return data as T
}

export const api = {
  listVacancies: () => request<Vacancy[]>('/api/vacancies'),

  getVacancy: (id: number) => request<Vacancy>(`/api/vacancies/${id}`),

  createVacancy: (body: VacancyRequest) =>
    request<Vacancy>('/api/vacancies', {
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
}
