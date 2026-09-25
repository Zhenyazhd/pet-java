import { removeAt, setAt, updateAt } from '../../lib/list'
import { isSelected } from '../../lib/resumeFocus'
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
          onChange={(name) => onPatch((r) => ({ ...r, name }))}
        />
        <CvField
          className="cv-headline"
          value={resume.headline}
          onChange={(headline) => onPatch((r) => ({ ...r, headline }))}
        />
        <div className="cv-contacts">
          <CvField value={resume.phone} onChange={(phone) => onPatch((r) => ({ ...r, phone }))} />
          <span>|</span>
          <CvField value={resume.email} onChange={(email) => onPatch((r) => ({ ...r, email }))} />
          <span>|</span>
          <CvField
            value={resume.linkedinLabel}
            onChange={(linkedinLabel) => onPatch((r) => ({ ...r, linkedinLabel }))}
          />
        </div>
      </div>

      <CvSection
        title="Profil"
        active={isSelected(selected, 'profile')}
        onSelect={(e) => onSelect('profile', undefined, e)}
      >
        <CvField
          multiline
          className="cv-body"
          value={resume.profile}
          onChange={(profile) => onPatch((r) => ({ ...r, profile }))}
        />
      </CvSection>

      <div className="cv-section">
        <h2 className="cv-section__title">Expérience professionnelle</h2>
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
        title="Education"
        active={isSelected(selected, 'education')}
        onSelect={(e) => onSelect('education', undefined, e)}
      >
        {resume.education.map((edu, index) => (
          <div key={index} className="cv-entry">
            <div className="cv-entry__row">
              <CvField
                className="cv-entry__title"
                value={edu.title}
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
        title="Réalisations"
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
        title="Compétences"
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
