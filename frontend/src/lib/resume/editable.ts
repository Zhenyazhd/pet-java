import type {
  AchievementItem,
  EducationItem,
  ExperienceItem,
  ResumeDocument,
  ResumeLocale,
  SkillItem,
} from '../../api/resumeTypes'

export type Keyed<T> = T & { key: string }

export type EditableResume = Omit<
  ResumeDocument,
  'experience' | 'education' | 'achievements' | 'skills'
> & {
  experience: Keyed<ExperienceItem>[]
  education: Keyed<EducationItem>[]
  achievements: Keyed<AchievementItem>[]
  skills: Keyed<SkillItem>[]
}

let counter = 0
export const newKey = () => `row-${++counter}`

export function resolveLocale(locale?: string | null): ResumeLocale {
  return locale === 'en' ? 'en' : 'fr'
}

function keyed<T extends object>(items: T[] | null | undefined): Keyed<T>[] {
  return (items ?? []).map((item) => ({ ...item, key: newKey() }))
}

function unkeyed<T extends object>(items: Keyed<T>[]): T[] {
  return items.map(({ key: _key, ...rest }) => rest as unknown as T)
}

export function toEditable(doc: ResumeDocument): EditableResume {
  return {
    ...doc,
    locale: resolveLocale(doc.locale),
    experience: keyed(doc.experience).map((job) => ({ ...job, bullets: job.bullets ?? [] })),
    education: keyed(doc.education),
    achievements: keyed(doc.achievements),
    skills: keyed(doc.skills),
  }
}

export function toDocument(resume: EditableResume): ResumeDocument {
  return {
    ...resume,
    experience: unkeyed(resume.experience),
    education: unkeyed(resume.education),
    achievements: unkeyed(resume.achievements),
    skills: unkeyed(resume.skills),
  }
}

export const EMPTY_ITEMS = {
  experience: (): Keyed<ExperienceItem> => ({ key: newKey(), title: '', subtitle: '', dates: '', bullets: [''] }),
  education: (): Keyed<EducationItem> => ({ key: newKey(), title: '', subtitle: '', location: '', details: '' }),
  achievements: (): Keyed<AchievementItem> => ({ key: newKey(), title: '', text: '' }),
  skills: (): Keyed<SkillItem> => ({ key: newKey(), category: '', items: '' }),
}
