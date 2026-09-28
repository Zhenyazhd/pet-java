export type ResumeLocale = 'fr' | 'en'

export type SectionTitles = {
  profile: string
  experience: string
  education: string
  achievements: string
  skills: string
}

/** Sheet / PDF section headings. */
export const SECTION_TITLES: Record<ResumeLocale, SectionTitles> = {
  fr: {
    profile: 'Profil',
    experience: 'Expérience professionnelle',
    education: 'Education',
    achievements: 'Réalisations',
    skills: 'Compétences',
  },
  en: {
    profile: 'Profile',
    experience: 'Professional Experience',
    education: 'Education',
    achievements: 'Achievements',
    skills: 'Skills',
  },
}

/** Short labels for AI focus chip. */
export const SECTION_FOCUS: Record<ResumeLocale, SectionTitles & { header: string; all: string }> = {
  fr: {
    header: 'En-tête',
    profile: 'Profil',
    experience: 'Expérience',
    education: 'Education',
    achievements: 'Réalisations',
    skills: 'Compétences',
    all: 'CV entier',
  },
  en: {
    header: 'Header',
    profile: 'Profile',
    experience: 'Experience',
    education: 'Education',
    achievements: 'Achievements',
    skills: 'Skills',
    all: 'Full resume',
  },
}

export function resolveLocale(locale?: string | null): ResumeLocale {
  return locale === 'en' ? 'en' : 'fr'
}
