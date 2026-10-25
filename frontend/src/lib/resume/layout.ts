import type { LayoutSettings } from '../../api/resumeTypes'

export type LayoutKey = Exclude<keyof LayoutSettings, 'autoFit'>

export type LayoutField = {
  key: LayoutKey
  label: string
  unit: string
  hint: string
  min: number
  max: number
  step: number
  /** The value the template uses when the setting is absent (core-api typst/resume.typ). */
  fallback: number
}

// min and max match LayoutSettings in ResumeDtos.java: the server rejects a save outside them.
export const LAYOUT_GROUPS: { title: string; fields: LayoutField[] }[] = [
  {
    title: 'Text',
    fields: [
      { key: 'fontSize', label: 'Font size', unit: 'pt', hint: 'Body text. Headings scale with it.', min: 8, max: 14, step: 0.1, fallback: 9.7 },
      { key: 'lineSpacing', label: 'Line spacing', unit: 'em', hint: 'Space between lines of one paragraph.', min: 0.2, max: 1.5, step: 0.05, fallback: 0.55 },
      { key: 'paragraphSpacing', label: 'Paragraph spacing', unit: 'em', hint: 'Space between paragraphs, e.g. under the profile.', min: 0, max: 2, step: 0.05, fallback: 0.55 },
    ],
  },
  {
    title: 'Spacing',
    fields: [
      { key: 'sectionSpacing', label: 'Above section titles', unit: 'em', hint: 'Space before Experience, Education and the other sections.', min: 0, max: 3, step: 0.05, fallback: 0.95 },
      { key: 'bulletSpacing', label: 'Between bullets', unit: 'em', hint: 'Space after each bullet point.', min: 0, max: 1.5, step: 0.05, fallback: 0.2 },
      { key: 'bulletsTopSpacing', label: 'Above the first bullet', unit: 'em', hint: 'Space between the description line and the first bullet of a job.', min: 0, max: 2, step: 0.05, fallback: 0.6 },
      { key: 'subtitleSpacing', label: 'Under job and school titles', unit: 'em', hint: 'Space between a title and the line below it, e.g. the company description.', min: 0, max: 1.5, step: 0.05, fallback: 0 },
    ],
  },
  {
    title: 'Page margins',
    fields: [
      { key: 'marginX', label: 'Left and right', unit: 'cm', hint: '', min: 0.5, max: 4, step: 0.1, fallback: 1.2 },
      { key: 'marginTop', label: 'Top', unit: 'cm', hint: '', min: 0.3, max: 4, step: 0.1, fallback: 0.8 },
      { key: 'marginBottom', label: 'Bottom', unit: 'cm', hint: '', min: 0.3, max: 4, step: 0.1, fallback: 0.75 },
    ],
  },
]

export const layoutValue = (layout: LayoutSettings | null | undefined, field: LayoutField): number =>
  layout?.[field.key] ?? field.fallback

/** Only well-formed numbers and a boolean survive: a stored or pasted layout never breaks the page. */
export function parseLayout(raw: unknown): LayoutSettings | undefined {
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) return undefined
  const source = raw as Record<string, unknown>
  const layout: LayoutSettings = {}
  for (const { fields } of LAYOUT_GROUPS) {
    for (const { key } of fields) {
      const value = source[key]
      if (typeof value === 'number' && Number.isFinite(value)) layout[key] = value
    }
  }
  if (typeof source.autoFit === 'boolean') layout.autoFit = source.autoFit
  return Object.keys(layout).length > 0 ? layout : undefined
}
