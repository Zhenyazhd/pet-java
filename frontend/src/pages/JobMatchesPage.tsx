import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { ActionBar } from '../components/jobs/ActionBar'
import { MatchesSection } from '../components/jobs/MatchesSection'
import { ProfileSummary } from '../components/jobs/ProfileSummary'
import { PageHeader } from '../components/ui/PageHeader'
import { useDocumentTitle } from '../lib/useDocumentTitle'
import { useFocusHeading } from '../lib/useFocusHeading'
import { useJobsContext } from '../lib/useJobsContext'

export function JobMatchesPage() {
  useDocumentTitle('Job matches')
  useFocusHeading('matches-page-title')
  const { search, state } = useJobsContext()
  const { clearErrors } = search

  // A start error from the profile page or an earlier visit is stale by now.
  useEffect(() => clearErrors(), [clearErrors])

  return (
    <section className="page" aria-labelledby="matches-page-title">
      <PageHeader
        eyebrow="Discovery"
        title="Job matches"
        titleId="matches-page-title"
        lead="Run a search, then review postings ranked against your search profile."
      />
      <div className="jobs">
        {state.profile ? (
          <>
            <ProfileSummary profile={state.profile} />
            <ActionBar search={search} state={state} />
            <MatchesSection refreshKey={search.finishedCount} />
          </>
        ) : (
          <section className="job-card" aria-labelledby="empty-title">
            <h2 id="empty-title" className="section-title">
              Start with your search profile
            </h2>
            <p>Tell us what work you want. Postings are collected from job boards and ranked against it.</p>
            <div>
              <Link to="/jobs/profile" className="btn btn--primary">
                Create your search profile
              </Link>
            </div>
          </section>
        )}
      </div>
    </section>
  )
}
