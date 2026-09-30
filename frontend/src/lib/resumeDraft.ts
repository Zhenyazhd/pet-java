import type { ResumeDocument } from '../types/resume'

const RESUME_DRAFT_PREFIX = 'job-search:resume-draft'

// Scoped per user id: an unscoped key would resurrect a stranger's (or a previous
// account's, after a DB reset) draft — including its now-meaningless version token —
// as soon as anyone else logs in on the same browser.
function draftKey(userId: number): string {
  return `${RESUME_DRAFT_PREFIX}:${userId}`
}

export function readResumeDraft(userId: number): ResumeDocument | null {
  try {
    const raw = localStorage.getItem(draftKey(userId))
    if (!raw) return null
    return JSON.parse(raw) as ResumeDocument
  } catch {
    localStorage.removeItem(draftKey(userId))
    return null
  }
}

export function writeResumeDraft(resume: ResumeDocument, userId: number): void {
  try {
    localStorage.setItem(draftKey(userId), JSON.stringify(resume))
  } catch {
    // Quota / private mode — editing still works in memory.
  }
}

export function clearResumeDraft(userId: number): void {
  localStorage.removeItem(draftKey(userId))
}

/** One-time cleanup: the pre-scoping draft key could hold anyone's stale content. */
export function pruneLegacyResumeDraft(): void {
  try {
    localStorage.removeItem(RESUME_DRAFT_PREFIX)
  } catch {
    // ignore
  }
}
