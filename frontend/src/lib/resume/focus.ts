import type { EditableResume } from './editable'
import { FOCUS_LABELS } from './locale'
import { resolveSelection, type Selection } from './selection'

export function focusLabel(resume: EditableResume, selected: Selection | null): string {
  const { selection, itemIndex } = resolveSelection(resume, selected)
  if (!selection) return FOCUS_LABELS.all
  if (itemIndex !== undefined) {
    return `Experience · ${resume.experience[itemIndex].title || `job ${itemIndex + 1}`}`
  }
  return FOCUS_LABELS[selection.section]
}
