import { removeAt, setAt, updateAt } from '../../lib/list'
import { isSelected } from '../../lib/resumeFocus'
import { resolveLocale, SECTION_TITLES } from '../../lib/resumeLocale'
import type { ResumeDocument, ResumeSection, Selection } from '../../types/resume'
import { CvField } from './CvField'
import { CvSection } from './CvSection'

type CvSheetProps = {
  resume: ResumeDocument
  selected: Selection | null
  onClearFocus: () => void
  onSelect: (section: ResumeSection, itemIndex?: number, e?: { stopPropagation(): void }) => void
  onPatch: (updater: (current: ResumeDocument) => ResumeDocument) => void
}

export function CvSheet({ resume, selected, onClearFocus, onSelect, onPatch }: CvSheetProps) {
  const titles = SECTION_TITLES[resolveLocale(resume.locale)]

  return (
    <article className="cv-sheet" onClick={onClearFocus}>
      <div
        role="button"
        tabIndex={0}
        className={`cv-block ${isSelected(selected, 'header') ? 'cv-block--active' : ''}`}
        onClick={(e) => onSelect('header', undefined, e)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') onSelect('header')
        }}
      >
        <CvField
          className="cv-name"
          value={resume.name}
          label="Full name"
          onChange={(name) => onPatch((r) => ({ ...r, name }))}
        />
        <CvField
          className="cv-headline"
          value={resume.headline}
          label="Headline"
          onChange={(headline) => onPatch((r) => ({ ...r, headline }))}
        />
        <div className="cv-contacts">
          <CvField
            value={resume.phone}
            label="Phone"
            onChange={(phone) => onPatch((r) => ({ ...r, phone }))}
          />
          <span>|</span>
          <CvField
            value={resume.email}
            label="Email"
            onChange={(email) => onPatch((r) => ({ ...r, email }))}
          />
          <span>|</span>
          <CvField
            value={resume.linkedinLabel}
            label="LinkedIn"
            onChange={(linkedinLabel) => onPatch((r) => ({ ...r, linkedinLabel }))}
          />
        </div>
      </div>

      <CvSection
        title={titles.profile}
        active={isSelected(selected, 'profile')}
        onSelect={(e) => onSelect('profile', undefined, e)}
      >
        <CvField
          multiline
          className="cv-body"
          value={resume.profile}
          label="Profile summary"
          onChange={(profile) => onPatch((r) => ({ ...r, profile }))}
        />
      </CvSection>

      <div className="cv-section">
        <h2 className="cv-section__title">{titles.experience}</h2>
        {resume.experience.map((job, index) => (
          <div
            key={index}
            role="button"
            tabIndex={0}
            className={`cv-block cv-entry ${
              isSelected(selected, 'experience', index) ? 'cv-block--active' : ''
            }`}
            onClick={(e) => onSelect('experience', index, e)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') onSelect('experience', index)
            }}
          >
            <div className="cv-entry__row">
              <CvField
                className="cv-entry__title"
                value={job.title}
                label={`Job ${index + 1} title`}
                onChange={(title) =>
                  onPatch((r) => ({
                    ...r,
                    experience: updateAt(r.experience, index, { title }),
                  }))
                }
              />
              <CvField
                className="cv-entry__dates"
                value={job.dates}
                label={`Job ${index + 1} dates`}
                onChange={(dates) =>
                  onPatch((r) => ({
                    ...r,
                    experience: updateAt(r.experience, index, { dates }),
                  }))
                }
              />
            </div>
            <CvField
              className="cv-entry__sub"
              value={job.subtitle}
              label={`Job ${index + 1} company / location`}
              onChange={(subtitle) =>
                onPatch((r) => ({
                  ...r,
                  experience: updateAt(r.experience, index, { subtitle }),
                }))
              }
            />
            <ul className="cv-list">
              {job.bullets.map((bullet, bIndex) => (
                <li key={bIndex} className="cv-list__item">
                  <CvField
                    multiline
                    className="cv-body"
                    value={bullet}
                    label={`Job ${index + 1} bullet ${bIndex + 1}`}
                    onChange={(text) =>
                      onPatch((r) => ({
                        ...r,
                        experience: updateAt(r.experience, index, {
                          bullets: setAt(r.experience[index].bullets, bIndex, text),
                        }),
                      }))
                    }
                  />
                  <button
                    type="button"
                    className="cv-icon-btn"
                    title="Remove bullet"
                    aria-label={`Remove job ${index + 1} bullet ${bIndex + 1}`}
                    onClick={(e) => {
                      e.stopPropagation()
                      onPatch((r) => ({
                        ...r,
                        experience: updateAt(r.experience, index, {
                          bullets: removeAt(r.experience[index].bullets, bIndex),
                        }),
                      }))
                    }}
                  >
                    ×
                  </button>
                </li>
              ))}
            </ul>
            <button
              type="button"
              className="cv-add"
              onClick={(e) => {
                e.stopPropagation()
                onPatch((r) => ({
                  ...r,
                  experience: updateAt(r.experience, index, {
                    bullets: [...r.experience[index].bullets, ''],
                  }),
                }))
              }}
            >
              + bullet
            </button>
          </div>
        ))}
        <button
          type="button"
          className="cv-add"
          onClick={(e) => {
            e.stopPropagation()
            onPatch((r) => ({
              ...r,
              experience: [
                ...r.experience,
                { title: 'New role', subtitle: '', dates: '', bullets: [''] },
              ],
            }))
          }}
        >
          + job
        </button>
      </div>

      <CvSection
        title={titles.education}
        active={isSelected(selected, 'education')}
        onSelect={(e) => onSelect('education', undefined, e)}
      >
        {resume.education.map((edu, index) => (
          <div key={index} className="cv-entry">
            <div className="cv-entry__row">
              <CvField
                className="cv-entry__title"
                value={edu.title}
                label={`School ${index + 1} name`}
                onChange={(title) =>
                  onPatch((r) => ({
                    ...r,
                    education: updateAt(r.education, index, { title }),
                  }))
                }
              />
              <CvField
                className="cv-entry__dates"
                value={edu.location}
                label={`School ${index + 1} location`}
                onChange={(location) =>
                  onPatch((r) => ({
                    ...r,
                    education: updateAt(r.education, index, { location }),
                  }))
                }
              />
            </div>
            <CvField
              className="cv-entry__sub"
              value={edu.subtitle}
              label={`School ${index + 1} degree / field`}
              onChange={(subtitle) =>
                onPatch((r) => ({
                  ...r,
                  education: updateAt(r.education, index, { subtitle }),
                }))
              }
            />
            <CvField
              multiline
              className="cv-body cv-body--small"
              value={edu.details}
              label={`School ${index + 1} details`}
              onChange={(details) =>
                onPatch((r) => ({
                  ...r,
                  education: updateAt(r.education, index, { details }),
                }))
              }
            />
          </div>
        ))}
        <button
          type="button"
          className="cv-add"
          onClick={(e) => {
            e.stopPropagation()
            onPatch((r) => ({
              ...r,
              education: [
                ...r.education,
                { title: 'New school', subtitle: '', location: '', details: '' },
              ],
            }))
          }}
        >
          + education
        </button>
      </CvSection>

      <CvSection
        title={titles.achievements}
        active={isSelected(selected, 'achievements')}
        onSelect={(e) => onSelect('achievements', undefined, e)}
      >
        <ul className="cv-list">
          {resume.achievements.map((item, index) => (
            <li key={index} className="cv-list__item cv-achievement">
              <div className="cv-list__grow">
                <CvField
                  className="cv-entry__title"
                  value={item.title}
                  label={`Achievement ${index + 1} title`}
                  onChange={(title) =>
                    onPatch((r) => ({
                      ...r,
                      achievements: updateAt(r.achievements, index, { title }),
                    }))
                  }
                />
                <CvField
                  multiline
                  className="cv-body"
                  value={item.text}
                  label={`Achievement ${index + 1} description`}
                  onChange={(text) =>
                    onPatch((r) => ({
                      ...r,
                      achievements: updateAt(r.achievements, index, { text }),
                    }))
                  }
                />
              </div>
              <button
                type="button"
                className="cv-icon-btn"
                title="Remove"
                aria-label={`Remove achievement ${index + 1}`}
                onClick={(e) => {
                  e.stopPropagation()
                  onPatch((r) => ({
                    ...r,
                    achievements: removeAt(r.achievements, index),
                  }))
                }}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
        <button
          type="button"
          className="cv-add"
          onClick={(e) => {
            e.stopPropagation()
            onPatch((r) => ({
              ...r,
              achievements: [...r.achievements, { title: 'New', text: '' }],
            }))
          }}
        >
          + achievement
        </button>
      </CvSection>

      <CvSection
        title={titles.skills}
        active={isSelected(selected, 'skills')}
        onSelect={(e) => onSelect('skills', undefined, e)}
      >
        <ul className="cv-list cv-list--skills">
          {resume.skills.map((skill, index) => (
            <li key={index} className="cv-list__item cv-skill">
              <div className="cv-list__grow">
                <CvField
                  className="cv-entry__title"
                  value={skill.category}
                  label={`Skill group ${index + 1} category`}
                  onChange={(category) =>
                    onPatch((r) => ({
                      ...r,
                      skills: updateAt(r.skills, index, { category }),
                    }))
                  }
                />
                <CvField
                  className="cv-body"
                  value={skill.items}
                  label={`Skill group ${index + 1} items`}
                  onChange={(items) =>
                    onPatch((r) => ({
                      ...r,
                      skills: updateAt(r.skills, index, { items }),
                    }))
                  }
                />
              </div>
              <button
                type="button"
                className="cv-icon-btn"
                title="Remove"
                aria-label={`Remove skill group ${index + 1}`}
                onClick={(e) => {
                  e.stopPropagation()
                  onPatch((r) => ({
                    ...r,
                    skills: removeAt(r.skills, index),
                  }))
                }}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
        <button
          type="button"
          className="cv-add"
          onClick={(e) => {
            e.stopPropagation()
            onPatch((r) => ({
              ...r,
              skills: [...r.skills, { category: 'New', items: '' }],
            }))
          }}
        >
          + skill
        </button>
      </CvSection>
    </article>
  )
}
