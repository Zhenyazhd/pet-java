import type { ResumeLocale } from '../../api/resumeTypes'
import { Button } from '../ui/Button'
import { LocaleSwitch } from './LocaleSwitch'

type ResumeToolbarProps = {
  locale: ResumeLocale
  dirty: boolean
  saving: boolean
  previewing: boolean
  blocked: boolean
  onLocaleChange: (locale: ResumeLocale) => void
  onPreview: () => void
  onSave: () => void
}

export function ResumeToolbar({
  locale,
  dirty,
  saving,
  previewing,
  blocked,
  onLocaleChange,
  onPreview,
  onSave,
}: ResumeToolbarProps) {
  return (
    <div className="resume-toolbar">
      <LocaleSwitch value={locale} onChange={onLocaleChange} />
      <span className="status-text resume-toolbar__dirty">{dirty ? 'Unsaved changes' : ''}</span>
      <Button variant="outline" aria-disabled={previewing || blocked} onClick={onPreview}>
        {previewing ? 'Preparing PDF…' : 'Preview PDF'}
      </Button>
      <Button aria-disabled={saving || blocked || !dirty} onClick={onSave}>
        {saving ? 'Saving…' : 'Save'}
      </Button>
    </div>
  )
}
