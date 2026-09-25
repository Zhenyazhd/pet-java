export type ExperienceItem = {
  title: string
  subtitle: string
  dates: string
  bullets: string[]
}

export type EducationItem = {
  title: string
  subtitle: string
  location: string
  details: string
}

export type AchievementItem = {
  title: string
  text: string
}

export type SkillItem = {
  category: string
  items: string
}

export type ResumeDocument = {
  name: string
  headline: string
  phone: string
  email: string
  linkedinUrl: string
  linkedinLabel: string
  profile: string
  experience: ExperienceItem[]
  education: EducationItem[]
  achievements: AchievementItem[]
  skills: SkillItem[]
  /** Section heading language on the sheet / PDF. */
  locale: 'fr' | 'en'
}

export type ResumeSection =
  | 'header'
  | 'profile'
  | 'experience'
  | 'education'
  | 'achievements'
  | 'skills'

/** AI focus: full resume or one section/block. */
export type AiScope = ResumeSection | 'all'

export type SuggestResponse = {
  section: string
  itemIndex?: number | null
  message: string
  proposed: unknown | null
}

export type ChatTurn = {
  role: 'user' | 'assistant'
  content: string
}

export type Selection = {
  section: ResumeSection
  itemIndex?: number
}

