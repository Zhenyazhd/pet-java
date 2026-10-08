import { LIMITS } from '../../lib/resume/limits'
import { EMPTY_ITEMS, type EditableResume } from '../../lib/resume/editable'
import { SECTION_TITLES } from '../../lib/resume/locale'
import type { Selection } from '../../lib/resume/selection'
import { CvBlock } from './CvBlock'
import { CvField } from './CvField'
import { ExperienceEditor } from './ExperienceEditor'
import { ListEditor } from './ListEditor'

type CvSheetProps = {
  resume: EditableResume
  selected: Selection | null
  onSelect: (selection: Selection) => void
  onPatch: (updater: (current: EditableResume) => EditableResume) => void
}

export function CvSheet({ resume, selected, onSelect, onPatch }: CvSheetProps) {
  const titles = SECTION_TITLES[resume.locale]
  const isActive = (section: Selection['section'], rowKey?: string) =>
    selected?.section === section && selected.rowKey === rowKey
  const set = <K extends keyof EditableResume>(key: K, value: EditableResume[K]) =>
    onPatch((current) => ({ ...current, [key]: value }))

  return (
    <article className="cv-sheet" lang={resume.locale} aria-label="Your CV">
      <CvBlock
        target="Header"
        active={isActive('header')}
        onFocus={() => onSelect({ section: 'header' })}
      >
        <CvField className="cv-name" value={resume.name} label="Full name" maxLength={LIMITS.name} required placeholder="Full name" onChange={(v) => set('name', v)} />
        <CvField className="cv-headline" value={resume.headline} label="Headline" maxLength={LIMITS.headline} placeholder="Headline" onChange={(v) => set('headline', v)} />
        <div className="cv-contacts">
          <CvField value={resume.phone} label="Phone" maxLength={LIMITS.phone} placeholder="Phone" onChange={(v) => set('phone', v)} />
          <CvField value={resume.email} label="Email" maxLength={LIMITS.email} placeholder="Email" onChange={(v) => set('email', v)} />
          <CvField value={resume.linkedinLabel} label="LinkedIn label" maxLength={LIMITS.linkedinLabel} placeholder="LinkedIn label" onChange={(v) => set('linkedinLabel', v)} />
          <CvField value={resume.linkedinUrl} label="LinkedIn URL" maxLength={LIMITS.linkedinUrl} placeholder="LinkedIn URL" onChange={(v) => set('linkedinUrl', v)} />
        </div>
      </CvBlock>

      <CvBlock
        title={titles.profile}
        target="Profile"
        active={isActive('profile')}
        onFocus={() => onSelect({ section: 'profile' })}
      >
        <CvField multiline value={resume.profile} label="Profile summary" maxLength={LIMITS.profile} onChange={(v) => set('profile', v)} />
      </CvBlock>

      <CvBlock
        title={titles.experience}
        target="all experience"
        active={isActive('experience')}
        onFocus={() => onSelect({ section: 'experience' })}
      >
        <ListEditor
          items={resume.experience}
          noun="job"
          create={EMPTY_ITEMS.experience}
          max={LIMITS.jobs}
          onChange={(items) => set('experience', items)}
          focus={{
            isActive: (index) => isActive('experience', resume.experience[index].key),
            onFocus: (index) =>
              onSelect({ section: 'experience', rowKey: resume.experience[index].key }),
            target: (index) => resume.experience[index].title || `job ${index + 1}`,
          }}
          renderItem={(job, index, update) => (
            <ExperienceEditor job={job} position={index + 1} update={update} />
          )}
        />
      </CvBlock>

      <CvBlock
        title={titles.education}
        target="Education"
        active={isActive('education')}
        onFocus={() => onSelect({ section: 'education' })}
      >
        <ListEditor
          items={resume.education}
          noun="education entry"
          create={EMPTY_ITEMS.education}
          max={LIMITS.education}
          onChange={(items) => set('education', items)}
          renderItem={(entry, index, update) => (
            <>
              <CvField className="cv-entry__title" value={entry.title} label={`Education ${index + 1} title`} maxLength={LIMITS.title} required placeholder="Degree" onChange={(title) => update({ title })} />
              <CvField className="cv-entry__sub" value={entry.subtitle} label={`Education ${index + 1} school`} maxLength={LIMITS.subtitle} placeholder="School" onChange={(subtitle) => update({ subtitle })} />
              <CvField value={entry.location} label={`Education ${index + 1} location`} maxLength={LIMITS.location} placeholder="Location" onChange={(location) => update({ location })} />
              <CvField multiline value={entry.details} label={`Education ${index + 1} details`} maxLength={LIMITS.details} placeholder="Details" onChange={(details) => update({ details })} />
            </>
          )}
        />
      </CvBlock>

      <CvBlock
        title={titles.achievements}
        target="Achievements"
        active={isActive('achievements')}
        onFocus={() => onSelect({ section: 'achievements' })}
      >
        <ListEditor
          items={resume.achievements}
          noun="achievement"
          create={EMPTY_ITEMS.achievements}
          max={LIMITS.achievements}
          onChange={(items) => set('achievements', items)}
          renderItem={(item, index, update) => (
            <>
              <CvField className="cv-entry__title" value={item.title} label={`Achievement ${index + 1} title`} maxLength={LIMITS.title} required placeholder="Title" onChange={(title) => update({ title })} />
              <CvField multiline value={item.text} label={`Achievement ${index + 1} text`} maxLength={LIMITS.text} placeholder="What you achieved" onChange={(text) => update({ text })} />
            </>
          )}
        />
      </CvBlock>

      <CvBlock
        title={titles.skills}
        target="Skills"
        active={isActive('skills')}
        onFocus={() => onSelect({ section: 'skills' })}
      >
        <ListEditor
          items={resume.skills}
          noun="skill group"
          create={EMPTY_ITEMS.skills}
          max={LIMITS.skills}
          onChange={(items) => set('skills', items)}
          renderItem={(item, index, update) => (
            <div>
              <CvField className="cv-skill__category" value={item.category} label={`Skill group ${index + 1} name`} maxLength={LIMITS.category} required placeholder="Category" onChange={(category) => update({ category })} />
              <CvField multiline value={item.items} label={`Skill group ${index + 1} skills`} maxLength={LIMITS.items} placeholder="Skills, comma separated" onChange={(items) => update({ items })} />
            </div>
          )}
        />
      </CvBlock>
    </article>
  )
}
