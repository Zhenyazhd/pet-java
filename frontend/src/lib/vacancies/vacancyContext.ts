import type { Vacancy } from '../../api/types'
import { LIMITS } from '../resume/limits'

export function buildVacancyContext(vacancy: Vacancy): string {
  const lines = [`Title: ${vacancy.title}`]
  if (vacancy.company?.trim()) lines.push(`Company: ${vacancy.company.trim()}`)
  if (vacancy.url.trim()) lines.push(`URL: ${vacancy.url.trim()}`)
  if (vacancy.description?.trim()) lines.push('', 'Description:', vacancy.description.trim())
  if (vacancy.requirements.length > 0) {
    lines.push('', 'Requirements:')
    for (const requirement of vacancy.requirements) {
      lines.push(`- ${requirement.name} (${requirement.required ? 'required' : 'optional'})`)
    }
  }
  return lines.join('\n').slice(0, LIMITS.vacancyContext)
}
