import type { ExperienceItem, ResumeDocument, ResumeSection, Selection } from '../types/resume'
import { resolveLocale } from './resumeLocale'

export function applyProposed(
  resume: ResumeDocument,
  selection: Selection,
  proposed: unknown,
): ResumeDocument {
  const { section, itemIndex } = selection
  switch (section) {
    case 'header': {
      const p = proposed as Partial<ResumeDocument>
      return {
        ...resume,
        name: String(p.name ?? resume.name),
        headline: String(p.headline ?? resume.headline),
        phone: String(p.phone ?? resume.phone),
        email: String(p.email ?? resume.email),
        linkedinUrl: String(p.linkedinUrl ?? resume.linkedinUrl),
        linkedinLabel: String(p.linkedinLabel ?? resume.linkedinLabel),
      }
    }
    case 'profile': {
      if (typeof proposed === 'string') {
        return { ...resume, profile: proposed }
      }
      const p = proposed as { profile?: string }
      return { ...resume, profile: String(p.profile ?? '') }
    }
    case 'experience': {
      if (itemIndex !== undefined) {
        const experience = [...resume.experience]
        experience[itemIndex] = proposed as ExperienceItem
        return { ...resume, experience }
      }
      return { ...resume, experience: proposed as ResumeDocument['experience'] }
    }
    case 'education':
      return { ...resume, education: proposed as ResumeDocument['education'] }
    case 'achievements':
      return { ...resume, achievements: proposed as ResumeDocument['achievements'] }
    case 'skills':
      return { ...resume, skills: proposed as ResumeDocument['skills'] }
  }
}

export function applyAiProposed(
  resume: ResumeDocument,
  section: string,
  itemIndex: number | null | undefined,
  proposed: unknown,
): ResumeDocument {
  if (section === 'all') {
    const next = proposed as ResumeDocument
    return {
      ...next,
      locale: resolveLocale(next.locale ?? resume.locale),
    }
  }
  return applyProposed(
    resume,
    {
      section: section as ResumeSection,
      ...(itemIndex === null || itemIndex === undefined ? {} : { itemIndex }),
    },
    proposed,
  )
}

export function formatProposed(proposed: unknown): string {
  if (typeof proposed === 'string') return proposed
  try {
    return JSON.stringify(proposed, null, 2)
  } catch {
    return String(proposed)
  }
}
