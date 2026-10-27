import type { LayoutSettings, LayoutTemplate } from '../../api/resumeTypes'

export type LayoutKey = Exclude<keyof LayoutSettings, 'autoFit' | 'template'>

/** What every template but classic reads from the settings. A template has its own value for the first three. */
const TEMPLATE_KEYS: LayoutKey[] = ['fontSize', 'lineSpacing', 'marginX', 'paragraphSpacing', 'bulletSpacing', 'bulletsTopSpacing', 'itemSpacing']
/** The templates with a job title line under the name, whose gap the "under the name" setting can change. */
const HEADLINE_TEMPLATES: LayoutTemplate[] = ['anchor', 'atlas', 'charter', 'codex', 'column', 'keyline', 'marker', 'quartz', 'terrace', 'vertex']
/** The templates whose sections have a gap above them that the "above section titles" setting can change. */
const SECTION_GAP_TEMPLATES: LayoutTemplate[] = ['anchor', 'charter', 'codex', 'keyline', 'terrace']
/** The templates that also read the top and bottom margins, each with its own values. */
const MARGIN_TEMPLATES: LayoutTemplate[] = ['creative', 'executive']
type TemplateDefaults = Pick<Record<LayoutKey, number>, 'fontSize' | 'lineSpacing' | 'marginX'> &
  Partial<Record<'marginTop' | 'marginBottom', number>>

/** The templates of core-api typst/ (ResumeDtos.TEMPLATE_IDS): the same sections and texts, a different look. */
export const TEMPLATES: { value: LayoutTemplate; label: string; defaults?: TemplateDefaults }[] = [
  { value: 'classic', label: 'Classic: centred, navy rules' },
  { value: 'anchor', label: 'Anchor: contact band at the foot', defaults: { fontSize: 10.5, lineSpacing: 0.65, marginX: 1.6 } },
  { value: 'atlas', label: 'Atlas: colour header, skills rail', defaults: { fontSize: 10, lineSpacing: 0.62, marginX: 1.4 } },
  { value: 'charter', label: 'Charter: heavy rule under the name', defaults: { fontSize: 10.5, lineSpacing: 0.63, marginX: 1.7 } },
  { value: 'codex', label: 'Codex: quiet, Computer Modern', defaults: { fontSize: 11, lineSpacing: 0.66, marginX: 2.1 } },
  { value: 'column', label: 'Column: contact and skills rail', defaults: { fontSize: 10, lineSpacing: 0.62, marginX: 1.4 } },
  { value: 'creative', label: 'Creative: warm paper background', defaults: { fontSize: 10, lineSpacing: 0.65, marginX: 1.5, marginTop: 1.5, marginBottom: 1.5 } },
  { value: 'crest', label: 'Crest: formal, centred serif', defaults: { fontSize: 10, lineSpacing: 0.62, marginX: 2 } },
  { value: 'executive', label: 'Executive: LaTeX moderncv style', defaults: { fontSize: 11, lineSpacing: 0.65, marginX: 1.5, marginTop: 1, marginBottom: 1.5 } },
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
      { key: 'headlineSpacing', label: 'Under the name', unit: 'em', hint: 'Space between your name and the job title line.', min: 0, max: 3, step: 0.05, fallback: 0 },
      { key: 'itemSpacing', label: 'Between skills and achievements', unit: 'em', hint: 'Space between the lines of Skills and of Achievements.', min: 0, max: 2, step: 0.05, fallback: 0.5 },
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

/** Whether the template reads this setting: classic everything but the skills spacing, the others only some. */
export function fieldApplies(field: LayoutField, template: LayoutTemplate | undefined): boolean {
  const id = template ?? 'classic'
  if (id === 'classic') return field.key !== 'itemSpacing'
  return (
    TEMPLATE_KEYS.includes(field.key) ||
    (field.key === 'sectionSpacing' && SECTION_GAP_TEMPLATES.includes(id)) ||
    (field.key === 'headlineSpacing' && HEADLINE_TEMPLATES.includes(id)) ||
    ((field.key === 'marginTop' || field.key === 'marginBottom') && MARGIN_TEMPLATES.includes(id))
  )
}

/** A setting the template has no number for: left empty, the template keeps its own look. */
export function leftToTemplate(field: LayoutField, template: LayoutTemplate | undefined): boolean {
  // Classic's gap under the name is -0.05 em, which the field's range cannot hold: empty means that.
  if ((template ?? 'classic') === 'classic') return field.key === 'headlineSpacing'
  const defaults = TEMPLATES.find((t) => t.value === template)?.defaults
  return defaults !== undefined && !(field.key in defaults)
}

/** The layout after choosing a template: the numbers of the old one do not carry over, only autoFit does. */
export const withTemplate = (layout: LayoutSettings | null | undefined, template: LayoutTemplate): LayoutSettings =>
  layout?.autoFit === undefined ? { template } : { autoFit: layout.autoFit, template }

/** The number to show for a setting; undefined for one the template leaves to its own look (see leftToTemplate). */
export const layoutValue = (layout: LayoutSettings | null | undefined, field: LayoutField): number | undefined =>
  layout?.[field.key] ??
  (leftToTemplate(field, layout?.template)
    ? undefined
    : (TEMPLATES.find((t) => t.value === layout?.template)?.defaults?.[field.key as keyof TemplateDefaults] ??
      field.fallback))

/** Only well-formed numbers in their range, a boolean and a known template survive: a stored or pasted layout never breaks the page. */
export function parseLayout(raw: unknown): LayoutSettings | undefined {
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) return undefined
  const source = raw as Record<string, unknown>
  const layout: LayoutSettings = {}
  for (const { fields } of LAYOUT_GROUPS) {
    for (const { key, min, max } of fields) {
      const value = source[key]
      if (typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max) layout[key] = value
    }
  }
  if (typeof source.autoFit === 'boolean') layout.autoFit = source.autoFit
  const template = TEMPLATES.find((t) => t.value === source.template)
  if (template) layout.template = template.value
  return Object.keys(layout).length > 0 ? layout : undefined
}
