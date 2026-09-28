import type { Vacancy } from '../api/types'

const PREPARE_CONTEXT_KEY = 'job-search:prepare-vacancy-context'

/** In-memory handoff so React Strict Mode remount can re-read before ack. */
let prepareHandoff: string | null = null

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

export function stashPrepareVacancyContext(text: string): void {
  const normalized = text.trim()
  if (!normalized) return
  prepareHandoff = normalized
  try {
    sessionStorage.setItem(PREPARE_CONTEXT_KEY, normalized)
  } catch {
    // ignore quota / private mode
  }
}

/** Read prepare-CV context without clearing (Safe for Strict Mode remount). */
export function takePrepareVacancyContext(): string | null {
  if (prepareHandoff) return prepareHandoff
  try {
    const value = sessionStorage.getItem(PREPARE_CONTEXT_KEY)
    if (value == null) return null
    const trimmed = value.trim()
    prepareHandoff = trimmed || null
    return prepareHandoff
  } catch {
    return null
  }
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
