import type {
  AchievementItem,
  EducationItem,
  ExperienceItem,
  ResumeDocument,
  SkillItem,
} from '../../api/resumeTypes'
import { resolveLocale } from './editable'

export type ListSection = 'experience' | 'education' | 'achievements' | 'skills'
export type ListItem = ExperienceItem | EducationItem | AchievementItem | SkillItem

type Fields = Record<string, 'string' | 'strings'>

const LIST_FIELDS: Record<ListSection, Fields> = {
  experience: { title: 'string', subtitle: 'string', dates: 'string', bullets: 'strings' },
  education: { title: 'string', subtitle: 'string', location: 'string', details: 'string' },
  achievements: { title: 'string', text: 'string' },
  skills: { category: 'string', items: 'string' },
}

export const LIST_SECTIONS = Object.keys(LIST_FIELDS) as ListSection[]

export const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

function parseItem(raw: unknown, fields: Fields): ListItem | null {
  if (!isRecord(raw)) return null
  const item: Record<string, string | string[]> = {}
  for (const [name, kind] of Object.entries(fields)) {
    const value = raw[name]
    if (kind === 'string') {
      if (value !== undefined && typeof value !== 'string') return null
      item[name] = value ?? ''
    } else {
      if (value !== undefined && !(Array.isArray(value) && value.every((v) => typeof v === 'string'))) {
        return null
      }
      item[name] = (value as string[] | undefined) ?? []
    }
  }
  return item as unknown as ListItem
}

export function parseItems(raw: unknown[], section: ListSection): ListItem[] | null {
  const items = raw.map((entry) => parseItem(entry, LIST_FIELDS[section]))
  return items.every((item) => item !== null) ? (items as ListItem[]) : null
}

const TEXT_FIELDS = [
  'name',
  'headline',
  'phone',
  'email',
  'linkedinUrl',
  'linkedinLabel',
  'profile',
] as const


export function parseDocument(raw: unknown): ResumeDocument | null {
  if (!isRecord(raw) || typeof raw.version !== 'number') return null
  const text: Record<string, string> = {}
  for (const name of TEXT_FIELDS) {
    const value = raw[name]
    if (value !== undefined && typeof value !== 'string') return null
    text[name] = value ?? ''
  }
  const lists: Record<string, ListItem[]> = {}
  for (const section of LIST_SECTIONS) {
    const entries = raw[section]
    if (!Array.isArray(entries)) return null
    const items = parseItems(entries, section)
    if (!items) return null
    lists[section] = items
  }
  return {
    ...(text as Pick<ResumeDocument, (typeof TEXT_FIELDS)[number]>),
    ...(lists as Pick<ResumeDocument, ListSection>),
    locale: resolveLocale(typeof raw.locale === 'string' ? raw.locale : null),
    version: raw.version,
  }
}

export function sameContent(a: ResumeDocument, b: ResumeDocument): boolean {
  const left = parseDocument({ ...a, version: 0 })
  const right = parseDocument({ ...b, version: 0 })
  return left !== null && right !== null && JSON.stringify(left) === JSON.stringify(right)
}
