import type { ResumeDocument } from '../types/resume'

const RESUME_DRAFT_KEY = 'job-search:resume-draft'

export function readResumeDraft(): ResumeDocument | null {
  try {
    const raw = localStorage.getItem(RESUME_DRAFT_KEY)
    if (!raw) return null
    return JSON.parse(raw) as ResumeDocument
  } catch {
    localStorage.removeItem(RESUME_DRAFT_KEY)
    return null
  }
}

export function writeResumeDraft(resume: ResumeDocument): void {
  try {
    localStorage.setItem(RESUME_DRAFT_KEY, JSON.stringify(resume))
  } catch {
    // Quota / private mode — editing still works in memory.
  }
}

export function clearResumeDraft(): void {
  localStorage.removeItem(RESUME_DRAFT_KEY)
}
