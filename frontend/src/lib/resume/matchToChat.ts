import type { MatchResponse } from '../../api/resumeTypes'
import { LIMITS } from './limits'

const MAX_MISSING = 20

export const MATCH_SHOWN_AS = 'Improve my CV using the vacancy match results.'

/** What "Send to AI chat" asks the AI: the report in a few lines, cut to the instruction limit. */
export function buildMatchInstruction(report: MatchResponse): string {
  const platforms = report.platforms
    .map((p) => `${p.system} ${p.overallScore}/100 (${p.passesFilter ? 'passes' : 'fails'})`)
    .join(', ')
  const missing = [
    ...new Set(
      report.platforms.flatMap((p) => Object.values(p.breakdown ?? {}).flatMap((dimension) => dimension?.missing ?? [])),
    ),
  ]
  const suggestions = report.suggestions.map(
    (s) => `- [${s.impact}] ${s.summary}${s.details.length > 0 ? ': ' + s.details.join('; ') : ''}`,
  )
  const lines = [
    'An ATS check of my CV against the chosen vacancy gave these results. Improve my CV with them.',
    'Use only facts already in my CV or career path; do not invent anything. Say what you could not fix.',
    `Average ${report.averageScore}/100. ${platforms}.`,
    suggestions.length > 0 ? `Suggestions:\n${suggestions.join('\n')}` : '',
    // Last and capped: if the text still hits the limit, it is the least useful part that is cut.
    missing.length > 0 ? `Missing: ${missing.slice(0, MAX_MISSING).join('; ')}.` : '',
  ]
  return lines.filter(Boolean).join('\n').slice(0, LIMITS.instruction)
}
