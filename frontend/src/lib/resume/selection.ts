import type { EditableResume } from './editable'
import type { ResumeSection } from '../../api/resumeTypes'

export type Selection = { section: ResumeSection; rowKey?: string }

export const sameTarget = (a: Selection | null, b: Selection) =>
  a?.section === b.section && a.rowKey === b.rowKey

export function resolveSelection(
  resume: EditableResume | null,
  selected: Selection | null,
): { selection: Selection | null; itemIndex?: number } {
  if (!selected) return { selection: null }
  if (selected.rowKey === undefined) return { selection: selected }
  if (!resume || selected.section !== 'experience') return { selection: null }
  const itemIndex = resume.experience.findIndex((job) => job.key === selected.rowKey)
  return itemIndex === -1 ? { selection: null } : { selection: selected, itemIndex }
}
