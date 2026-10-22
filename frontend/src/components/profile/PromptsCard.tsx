import { formatCounter } from '../../lib/format'
import { useId, useState } from 'react'
import type { PromptPart } from '../../api/types'
import { errorMessage } from '../../lib/abort'
import { classNames } from '../../lib/classNames'
import { PROMPT_MAX, usePromptParts } from '../../lib/promptParts'
import { useBeforeUnload } from '../../lib/useBeforeUnload'
import { Banner } from '../ui/Banner'
import { Button } from '../ui/Button'

const WARNED_KEY = 'truthfulness'
const CHANGED_TEXT =
  'The built-in text for this part has changed since you edited it. Your version is still used. Reset to default to get the new text.'
const WARNING_TEXT =
  'Loosening this lets the assistant write things that are not true on your CV. You are responsible for what you send to employers.'

export function PromptsCard() {
  const { parts, loading, loadError, retry, save, reset } = usePromptParts()

  return (
    <section className="prompts" aria-labelledby="prompts-title" aria-busy={loading}>
      <hr className="rule" />
      <div className="prompts__intro">
        <h2 id="prompts-title" className="prompts__title">
          AI assistant instructions
        </h2>
        <p className="field__hint">
          This is the text the AI chat follows when it edits your CV. The defaults are used until you
          change them. Changes apply to your next message.
        </p>
      </div>

      {loadError ? (
        <div className="stack">
          <Banner tone="error">{loadError}</Banner>
          <div>
            <Button variant="outline" onClick={retry}>
              Try again
            </Button>
          </div>
        </div>
      ) : (
        <>
          <p className="status-text" role="status">
            {loading ? 'Loading…' : ''}
          </p>
          {parts.map((part) => (
            <PromptBlock key={part.key} part={part} onSave={save} onReset={reset} />
          ))}
        </>
      )}
    </section>
  )
}

type BlockProps = {
  part: PromptPart
  onSave: (key: string, text: string) => Promise<PromptPart>
  onReset: (part: PromptPart) => Promise<void>
}

function PromptBlock({ part, onSave, onReset }: BlockProps) {
  const id = useId()
  const warned = part.key === WARNED_KEY
  const baseline = part.customText ?? part.defaultText
  const [text, setText] = useState(baseline)
  const [busy, setBusy] = useState<'save' | 'reset' | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)

  // Same normalisation as the server, so the counter and the limit agree with it.
  const normalised = text.replace(/\r\n/g, '\n').trim()
  const length = normalised.length
  const over = length > PROMPT_MAX
  const dirty = normalised !== baseline
  const canSave = !busy && dirty && length > 0 && !over
  useBeforeUnload(dirty)

  async function run(kind: 'save' | 'reset', action: () => Promise<void>, ok: string, fail: string) {
    setBusy(kind)
    setError(null)
    setDone(null)
    try {
      await action()
      setDone(ok)
      return true
    } catch (err) {
      // The textarea keeps what the user typed.
      setError(errorMessage(err, fail))
      return false
    } finally {
      setBusy(null)
    }
  }

  function save() {
    if (!canSave) return
    void run(
      'save',
      async () => {
        const updated = await onSave(part.key, text)
        setText(updated.customText ?? updated.defaultText)
      },
      'Saved.',
      'Failed to save',
    )
  }

  async function reset() {
    if (busy || !part.customized) return
    const ok = await run('reset', () => onReset(part), 'Reset to the default.', 'Failed to reset')
    if (ok) setText(part.defaultText)
  }

  return (
    <div className="prompts__block">
      <div className="prompts__head">
        <h3 className="prompts__name">{part.title}</h3>
        {part.customized && <span className="badge">Customised</span>}
      </div>
      <p id={`${id}-desc`} className="field__hint">
        {part.description}
      </p>

      <div className="field">
        <div className="field__row">
          <label className="field__label small-caps" htmlFor={`${id}-text`}>
            {part.title} instructions
          </label>
          <span
            id={`${id}-count`}
            className={classNames('field__counter small-caps', over && 'prompts__over')}
          >
            {formatCounter(length, PROMPT_MAX)}
            {over && `, ${(length - PROMPT_MAX).toLocaleString('en-US')} over`}
          </span>
        </div>
        <textarea
          id={`${id}-text`}
          className="input input--area prompts__text"
          aria-describedby={classNames(`${id}-desc`, `${id}-count`, warned && `${id}-warn`, part.defaultChanged && `${id}-changed`)}
          aria-invalid={over || undefined}
          value={text}
          onChange={(e) => {
            setText(e.target.value)
            setDone(null)
          }}
          readOnly={busy !== null}
          rows={8}
        />
      </div>

      {warned && (
        <p id={`${id}-warn`} className="prompts__warning">
          {WARNING_TEXT}
        </p>
      )}

      {part.defaultChanged && (
        <p id={`${id}-changed`} className="prompts__warning">
          {CHANGED_TEXT}
        </p>
      )}

      {error && <Banner tone="error">{error}</Banner>}
      <div role="status">{done && !error && <Banner tone="ok">{done}</Banner>}</div>

      <div className="profile__actions">
        <Button aria-disabled={!canSave} onClick={save}>
          {busy === 'save' ? 'Saving…' : 'Save'}
        </Button>
        <Button
          variant="outline"
          aria-disabled={!part.customized || busy !== null}
          onClick={reset}
        >
          {busy === 'reset' ? 'Resetting…' : 'Reset to default'}
        </Button>
      </div>
      {part.customized && dirty && (
        <p className="field__hint">Reset replaces what you typed with the default.</p>
      )}
    </div>
  )
}
