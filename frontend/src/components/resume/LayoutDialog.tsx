import { useId, useState } from 'react'
import type { LayoutSettings, LayoutTemplate } from '../../api/resumeTypes'
import { LAYOUT_GROUPS, TEMPLATES, TEMPLATE_KEYS, layoutValue, type LayoutField } from '../../lib/resume/layout'
import { Button } from '../ui/Button'
import { Dialog } from '../ui/Dialog'
import { Field } from '../ui/Field'
import { Select } from '../ui/Select'

const TITLE_ID = 'layout-dialog-title'

type LayoutDialogProps = {
  layout: LayoutSettings | null | undefined
  onChange: (layout: LayoutSettings | null) => void
  onClose: () => void
}

export function LayoutDialog({ layout, onChange, onClose }: LayoutDialogProps) {
  // Bumped by "Reset" so the number inputs start again from the defaults.
  const [resets, setResets] = useState(0)
  const autoFit = layout?.autoFit ?? true
  const templateId = useId()
  const classic = (layout?.template ?? 'classic') === 'classic'
  const groups = LAYOUT_GROUPS.map((group) => ({
    ...group,
    fields: group.fields.filter((field) => classic || TEMPLATE_KEYS.includes(field.key)),
  })).filter((group) => group.fields.length > 0)

  return (
    <Dialog labelledBy={TITLE_ID} onClose={onClose}>
      <header className="dialog__header">
        <h2 id={TITLE_ID} className="dialog__title">
          Page settings
        </h2>
        <Button variant="ghost" onClick={onClose}>
          Done
        </Button>
      </header>
      <p className="field__hint">
        These change how the PDF is typeset. They are saved with the CV; press Preview PDF to see the result.
      </p>

      <div className="field">
        <label className="field__label small-caps" htmlFor={templateId}>
          Template
        </label>
        <Select
          id={templateId}
          className="input"
          value={layout?.template ?? 'classic'}
          onChange={(template) => onChange({ ...layout, template: template as LayoutTemplate })}
          options={TEMPLATES}
        />
      </div>

      {groups.map((group) => (
        <fieldset key={group.title} className="layout-group">
          <legend className="small-caps">{group.title}</legend>
          <div className="layout-form">
            {group.fields.map((field) => (
              <LayoutNumber
                key={`${field.key}-${resets}-${layout?.template ?? "classic"}`}
                field={field}
                value={layoutValue(layout, field)}
                onCommit={(value) => onChange({ ...layout, [field.key]: value })}
              />
            ))}
          </div>
        </fieldset>
      ))}

      <label className="layout-check">
        <input
          type="checkbox"
          checked={autoFit}
          onChange={(e) => onChange({ ...layout, autoFit: e.target.checked })}
        />
        <span className="layout-check__text">
          Fit to one page automatically
          <span className="field__hint">
            On: the type is scaled by a few percent so the text fills exactly one page, which also moves the sizes
            above. Off: the PDF uses exactly your numbers and may run to a second page.
          </span>
        </span>
      </label>

      <div className="dialog__footer">
        <Button
          variant="outline"
          onClick={() => {
            onChange(null)
            setResets((n) => n + 1)
          }}
        >
          Reset to defaults
        </Button>
      </div>
    </Dialog>
  )
}

type LayoutNumberProps = { field: LayoutField; value: number; onCommit: (value: number) => void }

/** Keeps what is typed while it is half-written; only a number inside the range reaches the CV. */
function LayoutNumber({ field, value, onCommit }: LayoutNumberProps) {
  const [text, setText] = useState(String(value))
  const number = Number(text)
  const valid = text.trim() !== '' && Number.isFinite(number) && number >= field.min && number <= field.max

  return (
    <Field
      label={`${field.label} (${field.unit})`}
      hint={field.hint || undefined}
      type="number"
      inputMode="decimal"
      min={field.min}
      max={field.max}
      step={field.step}
      value={text}
      error={valid ? null : `Enter a number from ${field.min} to ${field.max}.`}
      onChange={(e) => {
        setText(e.target.value)
        const next = Number(e.target.value)
        if (e.target.value.trim() !== '' && Number.isFinite(next) && next >= field.min && next <= field.max) {
          onCommit(next)
        }
      }}
    />
  )
}
