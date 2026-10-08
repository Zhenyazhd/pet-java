import type { Ref } from 'react'
import { describeChange } from '../../lib/resume/proposal'
import type { ChatItem } from '../../lib/resume/useResumeAi'
import { Button } from '../ui/Button'

type ChatMessageProps = {
  item: ChatItem
  onApply: (id: number) => void
  onDismiss: (id: number) => void
  undo?: { onUndo: () => void; buttonRef: Ref<HTMLButtonElement> }
}

export function ChatMessage({ item, onApply, onDismiss, undo }: ChatMessageProps) {
  if (item.role === 'note') {
    return <p className="chat-note">{item.text}</p>
  }
  if (item.role === 'user') {
    return (
      <div className="chat-msg chat-msg--user">
        <p>{item.text}</p>
      </div>
    )
  }
  return (
    <div className="chat-msg chat-msg--assistant">
      <p>{item.text}</p>
      {item.problem && <p className="chat-msg__problem">{item.problem}</p>}
      {item.change && item.state === 'open' && (
        <div className="proposal">
          <p className="small-caps">Proposed edit</p>
          <pre className="proposal__text" tabIndex={0} role="region" aria-label="Proposed text">{describeChange(item.change)}</pre>
          <div className="proposal__actions">
            <Button onClick={() => onApply(item.id)}>Apply to sheet</Button>
            <Button variant="ghost" onClick={() => onDismiss(item.id)}>
              Dismiss
            </Button>
          </div>
        </div>
      )}
      {item.change && item.state !== 'open' && (
        <p className="status-text muted-line chat-msg__outcome">
          {item.state === 'applied' ? 'Applied to the sheet.' : 'Dismissed.'}
          {undo && item.state === 'applied' && (
            <Button ref={undo.buttonRef} variant="ghost" onClick={undo.onUndo}>
              Undo
            </Button>
          )}
        </p>
      )}
    </div>
  )
}
