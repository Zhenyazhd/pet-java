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

export type ResumeLocale = 'fr' | 'en'

/** How the PDF is typeset; every field is optional and a missing one keeps the template's own value. */
export type LayoutSettings = {
  fontSize?: number
  lineSpacing?: number
  paragraphSpacing?: number
  sectionSpacing?: number
  bulletSpacing?: number
  subtitleSpacing?: number
  bulletsTopSpacing?: number
  marginX?: number
  marginTop?: number
  marginBottom?: number
  /** True or missing: the type is scaled a few percent so the text fills exactly one page. */
  autoFit?: boolean
}

/** The wire format of GET/PUT /api/resume. */
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
  locale: ResumeLocale
  layout?: LayoutSettings | null
  /** Optimistic-concurrency token: echoed back on save, a stale one is rejected with 409. */
  version: number
}

export type ResumeSection =
  | 'header'
  | 'profile'
  | 'experience'
  | 'education'
  | 'achievements'
  | 'skills'

export type AiScope = ResumeSection | 'all'

export type ChatTurn = { role: 'user' | 'assistant'; content: string }

export type SuggestResponse = {
  section: string
  itemIndex?: number | null
  message: string
  proposed: unknown | null
}

export type MatchPlatformScore = {
  system: string
  vendor: string
  overallScore: number
  passesFilter: boolean
}

export type MatchSuggestion = {
  summary: string
  details: string[]
  impact: string
  platforms: string[]
}

export type ReadabilityIssue = {
  code: string
  severity: 'error' | 'warning' | 'info'
  message: string
}

/** What the ats-worker says about a built PDF; `text` is what an ATS reads from it. */
export type ReadabilityReport = {
  verdict: 'ok' | 'warnings' | 'issues'
  score: number
  pages: number
  coverage: number | null
  orderScore: number | null
  lostLines: string[]
  sectionsFound: string[]
  sectionsMissing: string[]
  contacts: { email: boolean; phone: boolean; url: boolean; nameAtTop: boolean }
  issues: ReadabilityIssue[]
  text: string
  textTruncated: boolean
}

export type MatchResponse = {
  averageScore: number
  platforms: MatchPlatformScore[]
  suggestions: MatchSuggestion[]
  provider?: string | null
  cached: boolean
  summary: string
}
