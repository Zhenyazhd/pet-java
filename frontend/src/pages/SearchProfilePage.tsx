import type { MouseEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { SearchProfileCard } from '../components/jobs/SearchProfileCard'
import { Banner } from '../components/ui/Banner'
import { PageHeader } from '../components/ui/PageHeader'
import { useBeforeUnload } from '../lib/useBeforeUnload'
import { useDocumentTitle } from '../lib/useDocumentTitle'
import { useFocusHeading } from '../lib/useFocusHeading'
import { useJobsContext } from '../lib/useJobsContext'

export default function SearchProfilePage() {
  useDocumentTitle('Search profile')
  useFocusHeading('profile-page-title')
  const { search, state, form } = useJobsContext()
  const navigate = useNavigate()
  useBeforeUnload(form.dirty)

  const searchBlocker = search.active
    ? 'A run is in progress.'
    : search.starting || search.startingRank
      ? 'Another request is in progress.'
      : null

  // On failure the profile stays saved and the reason is shown here.
  async function saveAndSearch() {
    if (await search.startRun()) navigate('/jobs')
  }

  function onBack(event: MouseEvent) {
    const modified = event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0
    if (!modified && form.dirty && !window.confirm('Leave without saving your changes?')) event.preventDefault()
  }

  return (
    <section className="page" aria-labelledby="profile-page-title">
      <PageHeader
        eyebrow="Discovery"
        title="Search profile"
        titleId="profile-page-title"
        lead="Describe the work you want. Postings are collected from job boards and ranked against this."
      />
      <div className="jobs">
        <p>
          <Link to="/jobs" onClick={onBack}>
            ← Job matches
          </Link>
        </p>
        <SearchProfileCard
          form={form}
          hasProfile={state.profile !== null}
          onSave={search.saveProfile}
          onSaveAndSearch={saveAndSearch}
          searchBlocker={searchBlocker}
          ready={search.settled}
        />
        {search.runError && <Banner tone="error">{search.runError}</Banner>}
      </div>
    </section>
  )
}
