import { Link } from 'react-router-dom'
import type { JobSearchProfile } from '../../api/types'
import { regionLabel } from '../../lib/jobSearch'

const EXPECTATIONS_PREVIEW = 120

export function ProfileSummary({ profile }: { profile: JobSearchProfile }) {
  const wants = profile.expectations.trim()
  return (
    <section className="job-card profile-summary" aria-labelledby="summary-title">
      <div className="profile-summary__head">
        <h2 id="summary-title" className="section-title">
          Your search profile
        </h2>
        <Link to="/jobs/profile" className="btn btn--outline">
          Edit search profile
        </Link>
      </div>
      <dl className="profile-summary__facts">
        <div>
          <dt className="small-caps">Roles</dt>
          <dd>{profile.roles.join(', ') || 'None'}</dd>
        </div>
        <div>
          <dt className="small-caps">Regions</dt>
          <dd>{profile.regions.map(regionLabel).join(', ') || 'Anywhere'}</dd>
        </div>
        <div>
          <dt className="small-caps">Min score</dt>
          <dd>{profile.minScore}</dd>
        </div>
      </dl>
      {wants && (
        <p className="muted-line">
          {wants.length > EXPECTATIONS_PREVIEW ? `${wants.slice(0, EXPECTATIONS_PREVIEW).trimEnd()}…` : wants}
        </p>
      )}
    </section>
  )
}
