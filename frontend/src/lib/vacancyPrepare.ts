import type { NavigateFunction } from 'react-router-dom'
import type { Vacancy } from '../api/types'

const PREPARE_CONTEXT_KEY = 'job-search:prepare-vacancy-context'

export type PrepareVacancyHandoff = {
  context: string
  vacancyId: number | null
}

/** In-memory handoff so React Strict Mode remount can re-read before ack. */
let prepareHandoff: PrepareVacancyHandoff | null = null

/** Build resume vacancy-context text from a saved vacancy. */
export function buildVacancyContext(vacancy: Vacancy): string {
  const lines: string[] = []
  lines.push(`Title: ${vacancy.title}`)
  if (vacancy.company?.trim()) {
    lines.push(`Company: ${vacancy.company.trim()}`)
  }
  if (vacancy.url?.trim()) {
    lines.push(`URL: ${vacancy.url.trim()}`)
  }
  lines.push('')
  if (vacancy.description?.trim()) {
    lines.push('Description:')
    lines.push(vacancy.description.trim())
    lines.push('')
  }
  if (vacancy.requirements.length > 0) {
    lines.push('Requirements:')
    for (const req of vacancy.requirements) {
      const tag = req.required ? 'required' : 'optional'
      lines.push(`- [${tag}] ${req.name}`)
    }
  }
  return lines.join('\n').trim()
}

/** Alias — reading a pending handoff to check for conflicts is the same lookup as consuming it. */
function readPendingHandoff(): PrepareVacancyHandoff | null {
  return takePrepareVacancyContext()
}

function isSameHandoff(a: PrepareVacancyHandoff, b: PrepareVacancyHandoff): boolean {
  if (a.vacancyId != null && b.vacancyId != null) {
    return a.vacancyId === b.vacancyId
  }
  return a.context === b.context
}

/**
 * Stash Prepare-CV context for the resume page.
 * @returns false if another unacked handoff exists — pass `{ replace: true }` after user confirms.
 */
export function stashPrepareVacancyContext(
  text: string,
  vacancyId?: number,
  options?: { replace?: boolean },
): boolean {
  const normalized = text.trim()
  if (!normalized) return false
  const payload: PrepareVacancyHandoff = {
    context: normalized,
    vacancyId: vacancyId != null && Number.isFinite(vacancyId) ? vacancyId : null,
  }
  const pending = readPendingHandoff()
  if (pending && !options?.replace && !isSameHandoff(pending, payload)) {
    return false
  }
  prepareHandoff = payload
  try {
    sessionStorage.setItem(PREPARE_CONTEXT_KEY, JSON.stringify(payload))
  } catch {
    // ignore quota / private mode
  }
  return true
}

/** Read prepare-CV context without clearing (safe for Strict Mode remount). */
export function takePrepareVacancyContext(): PrepareVacancyHandoff | null {
  if (prepareHandoff) return prepareHandoff
  try {
    const value = sessionStorage.getItem(PREPARE_CONTEXT_KEY)
    if (value == null) return null
    const parsed = parseHandoff(value)
    prepareHandoff = parsed
    return prepareHandoff
  } catch {
    return null
  }
}

function parseHandoff(raw: string): PrepareVacancyHandoff | null {
  const trimmed = raw.trim()
  if (!trimmed) return null
  try {
    const parsed = JSON.parse(trimmed) as Partial<PrepareVacancyHandoff>
    if (typeof parsed?.context === 'string' && parsed.context.trim()) {
      const id = parsed.vacancyId
      return {
        context: parsed.context.trim(),
        vacancyId: typeof id === 'number' && Number.isFinite(id) ? id : null,
      }
    }
  } catch {
    // Legacy: plain text without vacancyId
  }
  return { context: trimmed, vacancyId: null }
}

/** Clear one-shot prepare context after it has been applied. */
export function acknowledgePrepareVacancyContext(): void {
  prepareHandoff = null
  try {
    sessionStorage.removeItem(PREPARE_CONTEXT_KEY)
  } catch {
    // ignore
  }
}

export function isNotApplied(vacancy: Vacancy): boolean {
  return vacancy.application?.status === 'NOT_APPLIED'
}

/**
 * Stash a vacancy's context for the resume page and navigate there,
 * confirming with the user before clobbering an unacknowledged prior handoff.
 * Shared by every "Prepare CV" entry point (vacancy row, vacancy detail page).
 */
export function prepareCvAndNavigate(vacancy: Vacancy, navigate: NavigateFunction): void {
  const context = buildVacancyContext(vacancy)
  const stashed = stashPrepareVacancyContext(context, vacancy.id)
  if (
    !stashed &&
    !confirm('Another vacancy was prepared but not applied yet. Replace it with this one?')
  ) {
    return
  }
  if (!stashed) {
    stashPrepareVacancyContext(context, vacancy.id, { replace: true })
  }
  navigate('/')
}
