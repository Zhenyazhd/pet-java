import type { ResumeDocument } from '../../api/resumeTypes'
import {
  isRecord,
  LIST_SECTIONS,
  parseItems,
  type ListItem,
  type ListSection,
} from './document'
import { newKey, type EditableResume, type Keyed } from './editable'

export type { ListSection }

const HEADER_FIELDS = ['name', 'headline', 'phone', 'email', 'linkedinUrl', 'linkedinLabel'] as const
type HeaderFields = Partial<Record<(typeof HEADER_FIELDS)[number], string>>
type WholeLists = Pick<ResumeDocument, ListSection>

const FIELD_LABELS: Record<keyof HeaderFields, string> = {
  name: 'Name',
  headline: 'Headline',
  phone: 'Phone',
  email: 'Email',
  linkedinUrl: 'LinkedIn URL',
  linkedinLabel: 'LinkedIn label',
}

export type ProposalChange =
  | { section: 'all'; fields: HeaderFields & { profile?: string }; lists: WholeLists }
  | { section: 'header'; fields: HeaderFields }
  | { section: 'profile'; text: string }
  | { section: ListSection; rowKey: string | null; items: ListItem[] }

export type ParsedProposal = { ok: true; change: ProposalChange } | { ok: false; error: string }

const fail = (error: string): ParsedProposal => ({ ok: false, error })

/** Absent fields are left out so that applying keeps the current text; wrong types reject. */
function parseOptionalStrings<K extends string>(raw: Record<string, unknown>, names: readonly K[]) {
  const fields: Partial<Record<K, string>> = {}
  for (const name of names) {
    const value = raw[name]
    if (value === undefined) continue
    if (typeof value !== 'string') return null
    fields[name] = value
  }
  return fields
}

function parseWhole(raw: unknown): ProposalChange | null {
  if (!isRecord(raw) || typeof raw.name !== 'string') return null
  const fields = parseOptionalStrings(raw, [...HEADER_FIELDS, 'profile'] as const)
  if (!fields) return null
  const lists: Partial<WholeLists> = {}
  for (const section of LIST_SECTIONS) {
    // A reply that drops a whole list would empty it on Apply, so every list must be there.
    if (!Array.isArray(raw[section])) return null
    const items = parseItems(raw[section], section)
    if (!items) return null
    ;(lists as Record<string, ListItem[]>)[section] = items
  }
  return { section: 'all', fields, lists: lists as WholeLists }
}


export function parseProposal(section: string, rowKey: string | null, raw: unknown): ParsedProposal {
  switch (section) {
    case 'all': {
      const change = parseWhole(raw)
      return change ? { ok: true, change } : fail('The suggested CV is incomplete or has an unexpected shape.')
    }
    case 'header': {
      const fields = isRecord(raw) ? parseOptionalStrings(raw, HEADER_FIELDS) : null
      return fields && Object.keys(fields).length > 0
        ? { ok: true, change: { section, fields } }
        : fail('The suggested header has an unexpected shape.')
    }
    case 'profile': {
      const text = isRecord(raw) ? raw.profile : raw
      return typeof text === 'string'
        ? { ok: true, change: { section, text } }
        : fail('The suggested profile has an unexpected shape.')
    }
    case 'experience':
    case 'education':
    case 'achievements':
    case 'skills': {
      const entries = rowKey === null ? raw : isRecord(raw) ? [raw] : null
      if (!Array.isArray(entries)) {
        return fail(
          rowKey === null
            ? `The suggested ${section} should be a list.`
            : 'The suggestion should be a single entry.',
        )
      }
      if (rowKey !== null && entries.length !== 1) return fail('The suggestion should be a single entry.')
      const items = parseItems(entries, section)
      return items
        ? { ok: true, change: { section, rowKey, items } }
        : fail(`The suggested ${section} has an unexpected shape.`)
    }
    default:
      return fail(`Unknown section "${section}".`)
  }
}

const withKeys = (items: ListItem[]): Keyed<ListItem>[] =>
  items.map((item) => ({ ...item, key: newKey() }))

const listFromAll = (lists: WholeLists, section: ListSection) => withKeys(lists[section])

export function applyProposal(resume: EditableResume, change: ProposalChange): EditableResume {
  switch (change.section) {
    case 'all':
      return {
        ...resume,
        ...change.fields,
        experience: listFromAll(change.lists, 'experience') as EditableResume['experience'],
        education: listFromAll(change.lists, 'education') as EditableResume['education'],
        achievements: listFromAll(change.lists, 'achievements') as EditableResume['achievements'],
        skills: listFromAll(change.lists, 'skills') as EditableResume['skills'],
      }
    case 'header':
      return { ...resume, ...change.fields }
    case 'profile':
      return { ...resume, profile: change.text }
    default: {
      const current = resume[change.section] as Keyed<ListItem>[]
      if (change.rowKey === null) return { ...resume, [change.section]: withKeys(change.items) }
      const index = current.findIndex((row) => row.key === change.rowKey)
      if (index === -1) return resume
      const next = current.map((row, i) => (i === index ? { ...change.items[0], key: row.key } : row))
      return { ...resume, [change.section]: next }
    }
  }
}

function describeItem(item: ListItem): string {
  return Object.values(item)
    .flatMap((value) => (Array.isArray(value) ? value.map((bullet) => `• ${bullet}`) : [value]))
    .filter((line) => line.trim())
    .join('\n')
}

export function describeChange(change: ProposalChange): string {
  switch (change.section) {
    case 'all':
      return [
        describeFields(change.fields),
        ...LIST_SECTIONS.flatMap((section) =>
          change.lists[section].map(describeItem),
        ),
      ]
        .filter(Boolean)
        .join('\n\n')
    case 'header':
      return describeFields(change.fields)
    case 'profile':
      return change.text
    default:
      return change.items.map(describeItem).join('\n\n')
  }
}

function describeFields(fields: HeaderFields & { profile?: string }): string {
  return Object.entries(fields)
    .map(([name, value]) => `${FIELD_LABELS[name as keyof HeaderFields] ?? 'Profile'}: ${value}`)
    .join('\n')
}
