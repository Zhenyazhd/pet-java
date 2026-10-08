import type { ResumeLocale } from '../../api/resumeTypes'

const OPTIONS: { value: ResumeLocale; label: string }[] = [
  { value: 'fr', label: 'FR' },
  { value: 'en', label: 'EN' },
]

type LocaleSwitchProps = { value: ResumeLocale; onChange: (locale: ResumeLocale) => void }

export function LocaleSwitch({ value, onChange }: LocaleSwitchProps) {
  return (
    <fieldset className="locale-switch">
      <legend className="sr-only">CV language (section headings)</legend>
      {OPTIONS.map((option) => (
        <label key={option.value} className="locale-switch__option">
          <input
            type="radio"
            name="cv-locale"
            checked={value === option.value}
            onChange={() => onChange(option.value)}
          />
          <span>{option.label}</span>
        </label>
      ))}
    </fieldset>
  )
}
