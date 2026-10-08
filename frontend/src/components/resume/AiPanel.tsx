import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { AI_MODELS } from '../../lib/resume/aiModels'
import { LIMITS } from '../../lib/resume/limits'
import type { ChatItem, SendResult } from '../../lib/resume/useResumeAi'
import { Banner } from '../ui/Banner'
import { Button } from '../ui/Button'
import { ChatMessage } from './ChatMessage'

type AiPanelProps = {
  focus: string
  focused: boolean
  chat: ChatItem[]
  model: string
  busy: boolean
  error: string | null
  canUndo: boolean
  hasVacancy: boolean
  holdSend: string | null
  onWholeCv: () => void
  onModelChange: (model: string) => void
  onSend: (instruction: string) => Promise<SendResult>
  onStop: () => void
  onApply: (id: number) => void
  onDismiss: (id: number) => void
  onUndo: () => void
}

export function AiPanel({
  focus,
  focused,
  chat,
  model,
  busy,
  error,
  canUndo,
  hasVacancy,
  holdSend,
  onWholeCv,
  onModelChange,
  onSend,
  onStop,
  onApply,
  onDismiss,
  onUndo,
}: AiPanelProps) {
  const [draft, setDraft] = useState('')
  const modelId = useId()
  const messageRef = useRef<HTMLTextAreaElement>(null)
  const undoRef = useRef<HTMLButtonElement>(null)
  const [focusNext, setFocusNext] = useState<'undo' | 'message' | null>(null)

  useEffect(() => {
    if (!focusNext) return
    ;(focusNext === 'undo' && undoRef.current ? undoRef.current : messageRef.current)?.focus()
    setFocusNext(null)
  }, [focusNext])

  function submit(event?: FormEvent) {
    event?.preventDefault()
    if (busy || holdSend || !draft.trim()) return
    const text = draft
    setDraft('')
    void onSend(text).then(({ answered }) => {
      if (!answered) setDraft((current) => (current === '' ? text : current))
    })
  }

  return (
    <aside className="ai-panel" aria-label="AI assistant">
      <div className="ai-panel__focus">
        <div>
          <p className="small-caps">Focus</p>
          <p className="ai-panel__focus-name">{focus}</p>
        </div>
        {focused && (
          <Button variant="ghost" onClick={onWholeCv}>
            Whole CV
          </Button>
        )}
      </div>

      <div className="field">
        <label className="field__label small-caps" htmlFor={modelId}>
          Model
        </label>
        <select
          id={modelId}
          className="input"
          value={model}
          onChange={(e) => onModelChange(e.target.value)}
        >
          {AI_MODELS.map((option) => (
            <option key={option.id} value={option.id}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      <div className="chat-log" role="log" aria-live="polite" aria-label="Conversation with the AI">
        {chat.length === 0 && (
          <p className="muted-line">
            {hasVacancy
              ? 'Try “tighten the profile for this vacancy”.'
              : 'Try “make my second job sound more senior”. Pick a vacancy above to aim the edits at it.'}
          </p>
        )}
        {chat.map((item) => (
          <ChatMessage
            key={item.id}
            item={item}
            onApply={(id) => {
              onApply(id)
              setFocusNext('undo')
            }}
            onDismiss={(id) => {
              onDismiss(id)
              setFocusNext('message')
            }}
          />
        ))}
      </div>

      {canUndo && (
        <Button
          ref={undoRef}
          variant="outline"
          onClick={() => {
            onUndo()
            setFocusNext('message')
          }}
        >
          Undo last AI edit
        </Button>
      )}
      {error && <Banner tone="error">{error}</Banner>}

      <form className="ai-panel__form" onSubmit={submit}>
        <label className="sr-only" htmlFor={`${modelId}-message`}>
          Message to the AI
        </label>
        <textarea
          ref={messageRef}
          id={`${modelId}-message`}
          className="input"
          rows={3}
          maxLength={LIMITS.instruction}
          value={draft}
          placeholder={`Ask about: ${focus}. Enter sends, Shift+Enter adds a line.`}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            // Enter that confirms an IME candidate must not send the unfinished message.
            if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault()
              submit()
            }
          }}
        />
        <div className="ai-panel__send">
          {busy ? (
            <Button variant="outline" onClick={onStop}>
              Stop
            </Button>
          ) : (
            <Button type="submit" aria-disabled={holdSend !== null || !draft.trim()}>
              Send
            </Button>
          )}
          <span className="status-text muted-line" role="status">
            {busy ? 'Thinking…' : (holdSend ?? '')}
          </span>
        </div>
      </form>
    </aside>
  )
}
