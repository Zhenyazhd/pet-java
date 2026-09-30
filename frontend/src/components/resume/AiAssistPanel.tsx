import type { FormEvent } from 'react'
import { AI_MODELS } from '../../lib/aiModels'
import { formatProposed } from '../../lib/resumeEdits'
import { focusLabel } from '../../lib/resumeFocus'
import type { ResumeDocument, Selection } from '../../types/resume'
import { Button } from '../ui/Button'

export type ChatItem =
  | { role: 'user'; text: string }
  | {
      role: 'assistant'
      text: string
      proposed?: unknown | null
      section: string
      itemIndex?: number | null
    }

type AiAssistPanelProps = {
  resume: ResumeDocument
  selected: Selection | null
  chat: ChatItem[]
  draft: string
  busy: boolean
  hasVacancyContext: boolean
  aiModel: string
  onAiModelChange: (model: string) => void
  onClearFocus: () => void
  onDraftChange: (value: string) => void
  onSend: (e: FormEvent) => void
  onApply: (item: Extract<ChatItem, { role: 'assistant' }>) => void
}

export function AiAssistPanel({
  resume,
  selected,
  chat,
  draft,
  busy,
  hasVacancyContext,
  aiModel,
  onAiModelChange,
  onClearFocus,
  onDraftChange,
  onSend,
  onApply,
}: AiAssistPanelProps) {
  return (
    <aside className="cv-assist">
      <div className="cv-assist__head">
        <div className="cv-assist__focus">
          <span className="cv-assist__focus-label">Focus</span>
          <strong>{focusLabel(resume, selected)}</strong>
        </div>
        {selected && (
          <Button variant="ghost" onClick={onClearFocus}>
            Full resume
          </Button>
        )}
      </div>

      <label className="cv-assist__model">
        <span className="cv-assist__model-label">Model</span>
        <select
          value={aiModel}
          onChange={(e) => onAiModelChange(e.target.value)}
          disabled={busy}
          aria-label="AI model"
        >
          {AI_MODELS.map((model) => (
            <option key={model.id} value={model.id}>
              {model.label}
            </option>
          ))}
        </select>
      </label>

      <p className="cv-assist__hint">
        One chat for this page
        {hasVacancyContext ? ', with vacancy context' : ''}. Click a block to narrow focus; click empty
        sheet space for the whole CV.
      </p>
      <div className="cv-assist__chat">
        {chat.length === 0 && (
          <p className="cv-assist__empty">e.g. “tighten the profile for this vacancy”</p>
        )}
        {chat.map((item, index) => (
          <div key={index} className={`cv-msg cv-msg--${item.role}`}>
            <p>{item.text}</p>
            {item.role === 'assistant' && item.proposed != null && (
              <div className="cv-msg__proposal">
                <pre className="cv-msg__diff">{formatProposed(item.proposed)}</pre>
                <Button onClick={() => onApply(item)}>Apply to sheet</Button>
              </div>
            )}
          </div>
        ))}
      </div>
      <form className="cv-assist__form" onSubmit={onSend}>
        <textarea
          value={draft}
          onChange={(e) => onDraftChange(e.target.value)}
          onKeyDown={(e) => {
            // Skip IME composition (CJK input): the Enter that commits a candidate
            // must not also submit the (still-incomplete) draft.
            if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault()
              if (!busy && draft.trim()) {
                e.currentTarget.form?.requestSubmit()
              }
            }
          }}
          placeholder={
            selected
              ? `Ask AI about ${focusLabel(resume, selected)}… (Enter to send)`
              : 'Ask AI about the full resume… (Enter to send)'
          }
          rows={3}
          disabled={busy}
        />
        <Button type="submit" disabled={busy || !draft.trim()}>
          {busy ? 'Thinking…' : 'Send'}
        </Button>
      </form>
    </aside>
  )
}
