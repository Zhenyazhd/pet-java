import type { JobMatch, JobRegion, JobRun, JobRunStatus, JobSearchProfile } from '../api/types'
import { safeHttpUrl } from './format'

export const EXPECTATIONS_MAX = 5_000
export const ROLES_MAX = 30
export const ROLE_LENGTH_MAX = 100
export const REGIONS_MAX = 20
export const CITY_MAX = 100
const DEFAULT_MIN_SCORE = 50

export type ProfileValues = {
  expectations: string
  roles: string[]
  regions: JobRegion[]
  minScore: number
}

const EMPTY_PROFILE: ProfileValues = {
  expectations: '',
  roles: [],
  regions: [],
  minScore: DEFAULT_MIN_SCORE,
}

export function toValues(profile: JobSearchProfile | null): ProfileValues {
  if (!profile) return EMPTY_PROFILE
  const { expectations, roles, regions, minScore } = profile
  return { expectations, roles, regions, minScore }
}

export function sameValues(a: ProfileValues, b: ProfileValues): boolean {
  return (
    a.expectations === b.expectations &&
    a.minScore === b.minScore &&
    a.roles.length === b.roles.length &&
    a.roles.every((role, i) => role === b.roles[i]) &&
    a.regions.length === b.regions.length &&
    a.regions.every((r, i) => r.country === b.regions[i].country && r.city === b.regions[i].city)
  )
}

export function isActive(status: JobRunStatus | undefined): boolean {
  return status === 'QUEUED' || status === 'RUNNING'
}

export function isValidScore(score: number): boolean {
  return Number.isInteger(score) && score >= 0 && score <= 100
}

export function regionLabel(region: JobRegion): string {
  return region.city ? `${region.city}, ${region.country}` : region.country
}

const money = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
})

export function formatUsd(amount: number): string {
  return money.format(Number.isFinite(amount) ? amount : 0)
}

const dateTime = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' })
const dateOnly = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium' })

/** Server dates come from job boards too, so an unparsable one must not throw during render. */
export function formatWhen(iso: string | null, withTime = false): string | null {
  if (!iso) return null
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return null
  return (withTime ? dateTime : dateOnly).format(date)
}

/** The link to a posting, or null: only plain http(s) addresses the vacancy API would accept too. */
export function postingUrl(url: string | null): string | null {
  if (!url || url.length > 2_000 || /\s/.test(url) || !/^https?:\/\//i.test(url)) return null
  return safeHttpUrl(url)
}

export function isRankOnly(run: JobRun): boolean {
  return run.mode === 'RANK_ONLY'
}

/** The provider's rate limit is only visible in the run's error text. */
export function isModelRateLimited(run: JobRun): boolean {
  return !!run.error && /rate.?limit|too many requests/i.test(run.error)
}

export function unrankedLabel(count: number): string {
  return `${count} ${count === 1 ? 'posting is' : 'postings are'} not scored yet`
}

export function runSummary(run: JobRun, unranked: number | null): string {
  if (isRankOnly(run)) {
    const left = unranked === null ? '' : `, ${unranked} left`
    switch (run.status) {
      case 'QUEUED':
        return 'Scoring queued. It will start shortly.'
      case 'RUNNING':
        return 'Scoring the postings already found…'
      case 'FAILED':
        return run.ranked > 0
          ? `Scoring stopped after ${run.ranked} scored.`
          : 'Scoring failed. Some postings may still be unscored.'
      default:
        return `Scoring finished: ${run.ranked} scored${left}`
    }
  }
  switch (run.status) {
    case 'QUEUED':
      return 'Search queued. It will start shortly.'
    case 'RUNNING':
      return 'Searching job boards and ranking what was found…'
    case 'SUCCEEDED':
      return `Search finished. ${run.newJobs} new ${run.newJobs === 1 ? 'posting' : 'postings'} found, ${run.ranked} ranked.`
    case 'PARTIAL':
      return `Search finished, but some sources failed; the others worked. ${run.newJobs} new ${run.newJobs === 1 ? 'posting' : 'postings'} found, ${run.ranked} ranked.`
    case 'FAILED':
      return 'Search failed. No new matches were added.'
  }
}

export function vacancyFromMatch(match: JobMatch, url: string) {
  const { job } = match
  return {
    url,
    title: job.title.trim().slice(0, 255) || 'Untitled posting',
    company: job.company?.trim().slice(0, 255) || null,
    description: job.snippet?.slice(0, 50_000) || null,
    matchPercent: Math.min(100, Math.max(0, Math.round(match.score))),
  }
}
