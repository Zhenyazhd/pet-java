import type { ResumeDocument } from '../../api/resumeTypes'
import { parseDocument, sameContent } from './document'

const DRAFT_PREFIX = 'job-search:resume-draft:'
const draftKey = (userId: number) => `${DRAFT_PREFIX}${userId}`

export function readResumeDraft(userId: number): ResumeDocument | null {
  try {
    const raw = localStorage.getItem(draftKey(userId))
    if (!raw) return null
    const draft = parseDocument(JSON.parse(raw))
    if (!draft) clearResumeDraft(userId)
    return draft
  } catch {
    return null
  }
}

export type DraftDecision = 'none' | 'drop' | 'restore' | 'stale'


export function decideDraft(draft: ResumeDocument | null, server: ResumeDocument): DraftDecision {
  if (!draft) return 'none'
  if (sameContent(draft, server)) return 'drop'
  return draft.version === server.version ? 'restore' : 'stale'
}

export function writeResumeDraft(userId: number, doc: ResumeDocument): void {
  try {
    localStorage.setItem(draftKey(userId), JSON.stringify(doc))
  } catch {
    // quota or private mode: editing still works in memory
  }
}

/** Every account's draft in this browser: for a session that ended, when the account is no longer known. */
export function clearAllResumeDrafts(): void {
  try {
    Object.keys(localStorage)
      .filter((key) => key.startsWith(DRAFT_PREFIX))
      .forEach((key) => localStorage.removeItem(key))
  } catch {
    // nothing to clear
  }
}

export function clearResumeDraft(userId: number): void {
  try {
    localStorage.removeItem(draftKey(userId))
  } catch {
    // nothing to clear
  }
}
