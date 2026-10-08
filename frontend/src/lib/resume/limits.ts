import type { EditableResume } from './editable'

export const LIMITS = {
  name: 200,
  headline: 300,
  phone: 100,
  email: 320,
  linkedinUrl: 500,
  linkedinLabel: 200,
  profile: 5000,
  jobs: 50,
  education: 30,
  achievements: 50,
  skills: 50,
  bullets: 50,
  bullet: 1000,
  title: 200,
  subtitle: 300,
  dates: 100,
  location: 200,
  details: 2000,
  text: 2000,
  category: 200,
  items: 2000,
  instruction: 4000,
  vacancyContext: 50_000,
} as const

type Rule = { name: string; label: string; max: number; required?: boolean }

const HEADER_RULES: Rule[] = [
  { name: 'name', label: 'Name', max: LIMITS.name, required: true },
  { name: 'headline', label: 'Headline', max: LIMITS.headline },
  { name: 'phone', label: 'Phone', max: LIMITS.phone },
  { name: 'email', label: 'Email', max: LIMITS.email },
  { name: 'linkedinUrl', label: 'LinkedIn URL', max: LIMITS.linkedinUrl },
  { name: 'linkedinLabel', label: 'LinkedIn label', max: LIMITS.linkedinLabel },
  { name: 'profile', label: 'Profile', max: LIMITS.profile },
]

const LIST_RULES = {
  experience: {
    noun: 'Job',
    max: LIMITS.jobs,
    rules: [
      { name: 'title', label: 'Title', max: LIMITS.title, required: true },
      { name: 'subtitle', label: 'Company and location', max: LIMITS.subtitle },
      { name: 'dates', label: 'Dates', max: LIMITS.dates },
    ],
  },
  education: {
    noun: 'Education',
    max: LIMITS.education,
    rules: [
      { name: 'title', label: 'Title', max: LIMITS.title, required: true },
      { name: 'subtitle', label: 'School', max: LIMITS.subtitle },
      { name: 'location', label: 'Location', max: LIMITS.location },
      { name: 'details', label: 'Details', max: LIMITS.details },
    ],
  },
  achievements: {
    noun: 'Achievement',
    max: LIMITS.achievements,
    rules: [
      { name: 'title', label: 'Title', max: LIMITS.title, required: true },
      { name: 'text', label: 'Text', max: LIMITS.text },
    ],
  },
  skills: {
    noun: 'Skill group',
    max: LIMITS.skills,
    rules: [
      { name: 'category', label: 'Name', max: LIMITS.category, required: true },
      { name: 'items', label: 'Skills', max: LIMITS.items },
    ],
  },
} satisfies Record<string, { noun: string; max: number; rules: Rule[] }>

function problemWith(value: string, rule: Rule, where: string): string | null {
  if (rule.required && !value.trim()) return `${where}${rule.label} is required.`
  if (value.length > rule.max) return `${where}${rule.label} is too long (at most ${rule.max} characters).`
  return null
}


export function introducedProblem(before: EditableResume, after: EditableResume): string | null {
  return firstInvalidField(before) ? null : firstInvalidField(after)
}

export function firstInvalidField(resume: EditableResume): string | null {
  for (const rule of HEADER_RULES) {
    const problem = problemWith(resume[rule.name as keyof EditableResume] as string, rule, '')
    if (problem) return problem
  }
  for (const [section, config] of Object.entries(LIST_RULES)) {
    const rows = resume[section as keyof typeof LIST_RULES] as unknown as Record<string, unknown>[]
    if (rows.length > config.max) return `${config.noun} list: at most ${config.max} entries.`
    for (const [index, row] of rows.entries()) {
      const where = `${config.noun} ${index + 1} · `
      for (const rule of config.rules) {
        const problem = problemWith(row[rule.name] as string, rule, where)
        if (problem) return problem
      }
      const bullets = row.bullets as string[] | undefined
      if (bullets && bullets.length > LIMITS.bullets) return `${where}at most ${LIMITS.bullets} bullets.`
      if (bullets?.some((bullet) => bullet.length > LIMITS.bullet)) {
        return `${where}a bullet is too long (at most ${LIMITS.bullet} characters).`
      }
    }
  }
  return null
}
