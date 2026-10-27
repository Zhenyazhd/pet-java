import type { LayoutSettings, LayoutTemplate } from '../../api/resumeTypes'

export type LayoutKey = Exclude<keyof LayoutSettings, 'autoFit' | 'template'>

/** The templates of core-api typst/ (ResumeDtos.TEMPLATE_IDS): the same sections and texts, a different look. */
/** What a template reads from the settings, and its own value for each (classic reads them all, with the field fallbacks). */
export const TEMPLATE_KEYS: LayoutKey[] = ['fontSize', 'lineSpacing', 'marginX']
type TemplateDefaults = Pick<Record<LayoutKey, number>, 'fontSize' | 'lineSpacing' | 'marginX'>

export const TEMPLATES: { value: LayoutTemplate; label: string; defaults?: TemplateDefaults }[] = [
  { value: 'classic', label: 'Classic: centred, navy rules' },
  { value: 'anchor', label: 'Anchor: contact band at the foot', defaults: { fontSize: 10.5, lineSpacing: 0.65, marginX: 1.6 } },
  { value: 'atlas', label: 'Atlas: colour header, skills rail', defaults: { fontSize: 10, lineSpacing: 0.62, marginX: 1.4 } },
  { value: 'charter', label: 'Charter: heavy rule under the name', defaults: { fontSize: 10.5, lineSpacing: 0.63, marginX: 1.7 } },
  { value: 'codex', label: 'Codex: quiet, Computer Modern', defaults: { fontSize: 11, lineSpacing: 0.66, marginX: 2.1 } },
  { value: 'column', label: 'Column: contact and skills rail', defaults: { fontSize: 10, lineSpacing: 0.62, marginX: 1.4 } },
  { value: 'creative', label: 'Creative: warm paper background', defaults: { fontSize: 10, lineSpacing: 0.65, marginX: 1.5 } },
  { value: 'crest', label: 'Crest: formal, centred serif', defaults: { fontSize: 10, lineSpacing: 0.62, marginX: 2 } },
  { value: 'executive', label: 'Executive: LaTeX moderncv style', defaults: { fontSize: 11, lineSpacing: 0.65, marginX: 1.5 } },
  { value: 'keyline', label: 'Keyline: sans, brown rules', defaults: { fontSize: 10.5, lineSpacing: 0.65, marginX: 1.7 } },
  { value: 'marker', label: 'Marker: sans, teal headings', defaults: { fontSize: 10, lineSpacing: 0.65, marginX: 1.5 } },
  { value: 'quartz', label: 'Quartz: grey side panel', defaults: { fontSize: 10, lineSpacing: 0.62, marginX: 1.4 } },
  { value: 'print', label: 'Print: clean, blue accents', defaults: { fontSize: 10, lineSpacing: 0.6, marginX: 1.8 } },
  { value: 'refined', label: 'Refined: serif, slate accents', defaults: { fontSize: 10, lineSpacing: 0.62, marginX: 2 } },
  { value: 'terrace', label: 'Terrace: Garamond, plum', defaults: { fontSize: 11, lineSpacing: 0.68, marginX: 2.2 } },
  { value: 'vertex', label: 'Vertex: Garamond, plum rules', defaults: { fontSize: 11, lineSpacing: 0.62, marginX: 1.5 } },
]

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
      { key: 'bulletsTopSpacing', label: 'Above the first bullet', unit: 'em', hint: 'Space between the description line and the first bullet of a job, or the details of a school.', min: 0, max: 2, step: 0.05, fallback: 0.6 },
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
  layout?.[field.key] ??
  TEMPLATES.find((t) => t.value === layout?.template)?.defaults?.[field.key as keyof TemplateDefaults] ??
  field.fallback

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
  const template = TEMPLATES.find((t) => t.value === source.template)
  if (template) layout.template = template.value
  return Object.keys(layout).length > 0 ? layout : undefined
}
