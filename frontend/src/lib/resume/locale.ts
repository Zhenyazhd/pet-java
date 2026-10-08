import type { ResumeLocale, ResumeSection } from '../../api/resumeTypes'

type TitledSection = Exclude<ResumeSection, 'header'>

export const SECTION_TITLES: Record<ResumeLocale, Record<TitledSection, string>> = {
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

export const FOCUS_LABELS: Record<ResumeSection | 'all', string> = {
  all: 'Whole CV',
  header: 'Header',
  profile: 'Profile',
  experience: 'Experience',
  education: 'Education',
  achievements: 'Achievements',
  skills: 'Skills',
}
