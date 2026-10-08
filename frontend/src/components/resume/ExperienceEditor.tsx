import type { ExperienceItem } from '../../api/resumeTypes'
import { LIMITS } from '../../lib/resume/limits'
import { removeAt } from '../../lib/resume/list'
import { CvField } from './CvField'

type ExperienceEditorProps = {
  job: ExperienceItem
  position: number
  update: (patch: Partial<ExperienceItem>) => void
}

export function ExperienceEditor({ job, position, update }: ExperienceEditorProps) {
  const label = (part: string) => `Job ${position} ${part}`

  function setBullet(index: number, text: string) {
    update({ bullets: job.bullets.map((bullet, i) => (i === index ? text : bullet)) })
  }

  return (
    <>
      <div className="cv-entry__row">
        <CvField
          className="cv-entry__title"
          value={job.title}
          label={label('title')}
          placeholder="Role"
          maxLength={LIMITS.title}
          required
          onChange={(title) => update({ title })}
        />
        <CvField
          className="cv-entry__dates"
          value={job.dates}
          label={label('dates')}
          placeholder="Dates"
          maxLength={LIMITS.dates}
          onChange={(dates) => update({ dates })}
        />
      </div>
      <CvField
        className="cv-entry__sub"
        value={job.subtitle}
        label={label('company and location')}
        placeholder="Company, location"
        maxLength={LIMITS.subtitle}
        onChange={(subtitle) => update({ subtitle })}
      />
      <ul className="cv-bullets">
        {job.bullets.map((bullet, index) => (
          <li key={index}>
            <CvField
              multiline
              value={bullet}
              maxLength={LIMITS.bullet}
              label={label(`bullet ${index + 1}`)}
              onChange={(text) => setBullet(index, text)}
            />
            <button
              type="button"
              className="cv-tool cv-tool--remove"
              aria-label={`Remove ${label(`bullet ${index + 1}`).toLowerCase()}`}
              onClick={() => update({ bullets: removeAt(job.bullets, index) })}
            >
              ×
            </button>
          </li>
        ))}
      </ul>
      <button
        type="button"
        className="cv-tool cv-tool--add"
        aria-disabled={job.bullets.length >= LIMITS.bullets}
        onClick={() => job.bullets.length < LIMITS.bullets && update({ bullets: [...job.bullets, ''] })}
      >
        {job.bullets.length >= LIMITS.bullets ? `At most ${LIMITS.bullets} bullets` : '+ Add bullet'}
      </button>
    </>
  )
}
