import { useEffect, useRef, useState, type ReactNode } from 'react'
import { classNames } from '../../lib/classNames'
import { moveAt, removeAt, updateAt } from '../../lib/resume/list'
import { Button } from '../ui/Button'
import { FocusButton } from './FocusButton'

type RowFocus = {
  isActive: (index: number) => boolean
  onFocus: (index: number) => void
  target: (index: number) => string
}

type ListEditorProps<T extends { key: string }> = {
  items: T[]
  noun: string
  create: () => T
  onChange: (items: T[]) => void
  max: number
  renderItem: (item: T, index: number, update: (patch: Partial<T>) => void) => ReactNode
  focus?: RowFocus
}

export function ListEditor<T extends { key: string }>({
  items,
  noun,
  create,
  onChange,
  max,
  renderItem,
  focus,
}: ListEditorProps<T>) {
  const full = items.length >= max
  const listRef = useRef<HTMLDivElement>(null)
  const addRef = useRef<HTMLButtonElement>(null)
  const [refocus, setRefocus] = useState<{ row: number; tool?: 'up' | 'down' } | null>(null)

  useEffect(() => {
    if (!refocus) return
    const rows = listRef.current?.querySelectorAll<HTMLElement>('.cv-row')
    const target = rows?.[Math.min(refocus.row, (rows?.length ?? 1) - 1)]
    const control = refocus.tool
      ? target?.querySelector<HTMLElement>(`[data-tool="${refocus.tool}"]`)
      : target?.querySelector<HTMLElement>('input, textarea')
    ;(control ?? addRef.current)?.focus()
    setRefocus(null)
  }, [refocus])
  return (
    <div className="cv-list" ref={listRef}>
      {items.map((item, index) => {
        const active = focus?.isActive(index) ?? false
        return (
          <div key={item.key} className={classNames('cv-row', active && 'cv-block--active')}>
            {renderItem(item, index, (patch) => onChange(updateAt(items, index, patch)))}
            <div className="cv-row__tools">
              {focus && (
                <FocusButton
                  target={focus.target(index)}
                  active={active}
                  onFocus={() => focus.onFocus(index)}
                />
              )}
              <button
                type="button"
                className="cv-tool"
                aria-label={`Move ${noun} ${index + 1} up`}
                data-tool="up"
                aria-disabled={index === 0}
                onClick={() => {
                  if (index === 0) return
                  onChange(moveAt(items, index, index - 1))
                  setRefocus({ row: index - 1, tool: 'up' })
                }}
              >
                ↑
              </button>
              <button
                type="button"
                className="cv-tool"
                aria-label={`Move ${noun} ${index + 1} down`}
                data-tool="down"
                aria-disabled={index === items.length - 1}
                onClick={() => {
                  if (index === items.length - 1) return
                  onChange(moveAt(items, index, index + 1))
                  setRefocus({ row: index + 1, tool: 'down' })
                }}
              >
                ↓
              </button>
              <button
                type="button"
                className="cv-tool cv-tool--remove"
                aria-label={`Remove ${noun} ${index + 1}`}
                onClick={() => {
                  onChange(removeAt(items, index))
                  setRefocus({ row: index })
                }}
              >
                Remove
              </button>
            </div>
          </div>
        )
      })}
      <Button
        ref={addRef}
        variant="ghost"
        className="cv-add"
        aria-disabled={full}
        onClick={() => {
          if (full) return
          onChange([...items, create()])
          setRefocus({ row: items.length })
        }}
      >
        {full ? `At most ${max} entries` : `+ Add ${noun}`}
      </Button>
    </div>
  )
}
