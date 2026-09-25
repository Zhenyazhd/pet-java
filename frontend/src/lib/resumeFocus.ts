import type { ResumeDocument, ResumeSection, Selection } from '../types/resume'

export const SECTION_LABEL: Record<ResumeSection, string> = {
  header: 'Header',
  profile: 'Profil',
  experience: 'Expérience',
  education: 'Education',
  achievements: 'Réalisations',
  skills: 'Compétences',
}

export function focusLabel(resume: ResumeDocument, selection: Selection | null): string {
  if (!selection) return 'Full resume'
  if (selection.section === 'experience' && selection.itemIndex !== undefined) {
    const job = resume.experience[selection.itemIndex]
    return job?.title || `Job #${selection.itemIndex + 1}`
  }
  return SECTION_LABEL[selection.section]
}

export function isSelected(
  selection: Selection | null,
  section: ResumeSection,
  itemIndex?: number,
): boolean {
  if (!selection || selection.section !== section) return false
  return selection.itemIndex === itemIndex
}
