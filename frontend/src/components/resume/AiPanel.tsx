import { useEffect, useId, useLayoutEffect, useRef, useState, type FormEvent } from 'react'
import { AI_MODELS } from '../../lib/resume/aiModels'
import { LIMITS } from '../../lib/resume/limits'
import type { ChatItem, SendResult } from '../../lib/resume/useResumeAi'
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

const WHOLE_CV_PROMPTS = ['Tighten the profile', 'Make the bullets stronger and measurable', 'Fix grammar and wording']
const FOCUSED_PROMPTS = ['Make this clearer', 'Make this more concise', 'Add measurable results']

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
  const logRef = useRef<HTMLDivElement>(null)
  const lastSent = useRef('')
  // The pressed button disappears; keyboard focus must land somewhere sensible, not on <body>.
  const [focusNext, setFocusNext] = useState<'undo' | 'message' | null>(null)

  useEffect(() => {
    if (!focusNext) return
    ;(focusNext === 'undo' && undoRef.current ? undoRef.current : messageRef.current)?.focus()
    setFocusNext(null)
  }, [focusNext])

  // The log follows the conversation: a reply is read from its first line, anything else from the bottom.
  useLayoutEffect(() => {
    const log = logRef.current
    const last = log?.lastElementChild as HTMLElement | null | undefined
    if (!log || !last) return
    const replyJustArrived = !busy && !error && chat[chat.length - 1]?.role === 'assistant'
    const top = replyJustArrived ? last.offsetTop - 8 : log.scrollHeight
    const smooth = !window.matchMedia('(prefers-reduced-motion: reduce)').matches
    log.scrollTo({ top, behavior: smooth ? 'smooth' : 'auto' })
  }, [chat.length, busy, error])

  // The field grows with what is typed, up to a few lines.
  useLayoutEffect(() => {
    const field = messageRef.current
    if (!field) return
    field.style.height = 'auto'
    // scrollHeight leaves out the borders, which border-box sizing counts.
    field.style.height = `${field.scrollHeight + field.offsetHeight - field.clientHeight}px`
  }, [draft])

  function send(text: string, restoreOnRefusal = false) {
    if (busy || holdSend || !text.trim()) return
    lastSent.current = text
    void onSend(text).then(({ answered, problem }) => {
      // A send that never went out (stopped, refused) gives the typed text back. A failure does not: its
      // bubble offers Try again, and offering the text in two places would resend it twice.
      if (restoreOnRefusal && !answered && !problem) setDraft((current) => (current === '' ? text : current))
    })
  }

  function submit(event?: FormEvent) {
    event?.preventDefault()
    if (busy || holdSend || !draft.trim()) return
    const text = draft
    setDraft('')
    send(text, true)
  }

  // Undo sits on the message it undoes, and only while it is the latest thing in the conversation.
  const undoItemId = canUndo
    ? [...chat].reverse().find((item) => item.role === 'assistant' && item.state === 'applied')?.id
    : undefined

  const prompts = [...(hasVacancy ? ['Tailor my CV for this vacancy'] : []), ...(focused ? FOCUSED_PROMPTS : WHOLE_CV_PROMPTS)]

  return (
    <aside className="ai-panel" aria-label="AI assistant">
      <div className="ai-panel__head">
        <div>
          <p className="small-caps">Focus</p>
          <p className="ai-panel__focus-name">{focus}</p>
        </div>
        <div className="ai-panel__tools">
          {focused && (
            <Button variant="ghost" onClick={onWholeCv}>
              Whole CV
            </Button>
          )}
          <label className="sr-only" htmlFor={modelId}>
            AI model
          </label>
          <select
            id={modelId}
            className="input ai-panel__model"
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
      </div>

      <div className="chat-log" ref={logRef} role="log" aria-label="Conversation with the AI">
        {chat.length === 0 && !busy && (
          <div className="chat-empty">
            <p className="muted-line">
              Ask for a change, or start with one of these.
              {!hasVacancy && ' Pick a vacancy above to aim the edits at it.'}
            </p>
            <div className="chips">
              {prompts.map((prompt) => (
                <button
                  key={prompt}
                  type="button"
                  className="chip"
                  aria-disabled={holdSend !== null}
                  onClick={() => {
                    send(prompt)
                    setFocusNext('message')
                  }}
                >
                  {prompt}
                </button>
              ))}
            </div>
          </div>
        )}
        {chat.map((item) => (
          <ChatMessage
            key={item.id}
            item={item}
            undo={
              item.id === undoItemId
                ? {
                    buttonRef: undoRef,
                    onUndo: () => {
                      onUndo()
                      setFocusNext('message')
                    },
                  }
                : undefined
            }
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
        {busy && (
          <div className="chat-msg chat-msg--assistant chat-msg--thinking">
            <span className="thinking-dots" aria-hidden="true">
              <i />
              <i />
              <i />
            </span>
            <span>Thinking…</span>
            <Button
              variant="ghost"
              onClick={() => {
                onStop()
                setFocusNext('message')
              }}
            >
              Stop
            </Button>
          </div>
        )}
        {!busy && error && (
          <div className="chat-msg chat-msg--error">
            <p>{error}</p>
            <Button
              variant="outline"
              aria-disabled={holdSend !== null}
              onClick={() => {
                send(lastSent.current)
                setFocusNext('message')
              }}
            >
              Try again
            </Button>
          </div>
        )}
      </div>

      <form className="composer" onSubmit={submit}>
        <label className="sr-only" htmlFor={`${modelId}-message`}>
          Message to the AI
        </label>
        <textarea
          ref={messageRef}
          id={`${modelId}-message`}
          className="input composer__field"
          rows={1}
          maxLength={LIMITS.instruction}
          value={draft}
          placeholder={`Ask about: ${focus}`}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            // Enter that confirms an IME candidate must not send the unfinished message.
            if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault()
              submit()
            }
          }}
        />
        <Button type="submit" aria-disabled={busy || holdSend !== null || !draft.trim()}>
          Send
        </Button>
      </form>
      <p className="composer__hint">
        <span>Enter sends, Shift+Enter adds a line.</span>
        {/* Present before it has text, so a reason to wait is announced. */}
        <span role="status">{holdSend ?? ''}</span>
      </p>
    </aside>
  )
}
