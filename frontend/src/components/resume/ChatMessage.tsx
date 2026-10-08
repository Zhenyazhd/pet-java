import { describeChange } from '../../lib/resume/proposal'
import type { ChatItem } from '../../lib/resume/useResumeAi'
import { Button } from '../ui/Button'

type ChatMessageProps = {
  item: ChatItem
  onApply: (id: number) => void
  onDismiss: (id: number) => void
}

export function ChatMessage({ item, onApply, onDismiss }: ChatMessageProps) {
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
        <p className="status-text muted-line">
          {item.state === 'applied' ? 'Applied to the sheet.' : 'Dismissed.'}
        </p>
      )}
    </div>
  )
}
