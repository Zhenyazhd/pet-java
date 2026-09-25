import type { ResumeDocument, ResumeSection, Selection } from '../types/resume'
import { resolveLocale, SECTION_FOCUS } from './resumeLocale'

export function focusLabel(resume: ResumeDocument, selection: Selection | null): string {
  const focus = SECTION_FOCUS[resolveLocale(resume.locale)]
  if (!selection) return focus.all
  if (selection.section === 'experience' && selection.itemIndex !== undefined) {
    const job = resume.experience[selection.itemIndex]
    return job?.title || `Job #${selection.itemIndex + 1}`
  }
  if (selection.section === 'header') return focus.header
  return focus[selection.section]
}

export function isSelected(
  selection: Selection | null,
  section: ResumeSection,
  itemIndex?: number,
): boolean {
  if (!selection || selection.section !== section) return false
  return selection.itemIndex === itemIndex
}
