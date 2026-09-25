import type {
  ChatTurn,
  ResumeDocument,
  AiScope,
  SuggestResponse,
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

export type Profile = {
  displayName: string
  email: string
  careerPath: string
}

export const api = {
  getResume: async (): Promise<ResumeDocument> => {
    const response = await fetch('/api/resume')
    if (!response.ok) throw new Error(await readErrorMessage(response))
    return response.json()
  },

  saveResume: async (resume: ResumeDocument): Promise<ResumeDocument> => {
    const response = await fetch('/api/resume', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(resume),
    })
    if (!response.ok) throw new Error(await readErrorMessage(response))
    return response.json()
  },

  compileResume: async (): Promise<Blob> => {
    const response = await fetch('/api/resume/compile', { method: 'POST' })
    if (!response.ok) throw new Error(await readErrorMessage(response))
    return response.blob()
  },

  suggestResumeSection: async (
    section: AiScope,
    instruction: string,
    itemIndex?: number,
    history?: ChatTurn[],
    vacancyContext?: string,
  ): Promise<SuggestResponse> => {
    const response = await fetch('/api/ai/resume/suggest', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        section,
        instruction,
        ...(itemIndex === undefined ? {} : { itemIndex }),
        ...(history && history.length > 0 ? { history } : {}),
        ...(vacancyContext?.trim() ? { vacancyContext: vacancyContext.trim() } : {}),
      }),
    })
    if (!response.ok) throw new Error(await readErrorMessage(response))
    return response.json()
  },

  getProfile: async (): Promise<Profile> => {
    const response = await fetch('/api/profile')
    if (!response.ok) throw new Error(await readErrorMessage(response))
    return response.json()
  },

  saveProfile: async (profile: Profile): Promise<Profile> => {
    const response = await fetch('/api/profile', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(profile),
    })
    if (!response.ok) throw new Error(await readErrorMessage(response))
    return response.json()
  },
}
