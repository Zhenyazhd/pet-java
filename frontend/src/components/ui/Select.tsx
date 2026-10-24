import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { classNames } from '../../lib/classNames'

export type SelectOption = { value: string; label: string }

type SelectProps = {
  options: SelectOption[]
  value: string
  onChange: (value: string) => void
  /** For a visible `<label htmlFor>`. */
  id?: string
  /** Looks of the closed control, e.g. `input`. */
  className?: string
  disabled?: boolean
  'aria-label'?: string
  'aria-describedby'?: string
}

type Place = { top?: number; bottom?: number; left?: number; right?: number; minWidth: number }

const LIST_MAX = 256
const ROW = 44

/**
 * A drop-in for `<select>`: the browser draws the open list of a native one and ignores the page theme.
 * Follows the ARIA "select-only combobox" pattern; focus stays on the button, the list is a fixed layer
 * so no overflow:hidden parent can clip it. shortcut: typeahead is a plain prefix match, no IME handling.
 */
export function Select({ options, value, onChange, id, className, disabled, ...aria }: SelectProps) {
  const autoId = useId()
  const listId = `${autoId}-list`
  const optionId = (i: number) => `${autoId}-option-${i}`
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const [place, setPlace] = useState<Place | null>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const list = useRef<HTMLUListElement>(null)
  const typed = useRef({ text: '', at: 0 })

  const selected = options.findIndex((o) => o.value === value)

  function show() {
    const rect = trigger.current?.getBoundingClientRect()
    if (!rect) return
    const below = window.innerHeight - rect.bottom
    const flip = below < Math.min(LIST_MAX, options.length * ROW + 8) && rect.top > below
    const alignRight = rect.left + 320 > window.innerWidth
    setPlace({
      minWidth: rect.width,
      ...(flip ? { bottom: window.innerHeight - rect.top + 4 } : { top: rect.bottom + 4 }),
      ...(alignRight ? { right: window.innerWidth - rect.right } : { left: rect.left }),
    })
    setActive(Math.max(0, selected))
    setOpen(true)
  }

  function choose(i: number) {
    if (options[i] && options[i].value !== value) onChange(options[i].value)
    setOpen(false)
  }

  // A page scroll or resize would leave the fixed list floating away from its button.
  useEffect(() => {
    if (!open) return
    const close = (event: Event) => {
      if (event.target instanceof Node && list.current?.contains(event.target)) return
      setOpen(false)
    }
    window.addEventListener('scroll', close, true)
    window.addEventListener('resize', close)
    return () => {
      window.removeEventListener('scroll', close, true)
      window.removeEventListener('resize', close)
    }
  }, [open])

  // Keep the highlighted row visible when the arrow keys run past the list's edge.
  useEffect(() => {
    const box = list.current
    const row = box?.children[active] as HTMLElement | undefined
    if (!open || !box || !row) return
    if (row.offsetTop < box.scrollTop) box.scrollTop = row.offsetTop
    else if (row.offsetTop + row.offsetHeight > box.scrollTop + box.clientHeight) {
      box.scrollTop = row.offsetTop + row.offsetHeight - box.clientHeight
    }
  }, [open, active, place])

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (disabled) return
    const last = options.length - 1
    if (!open) {
      if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
        event.preventDefault()
        show()
      }
      return
    }
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault()
        setActive((i) => Math.min(last, i + 1))
        break
      case 'ArrowUp':
        event.preventDefault()
        setActive((i) => Math.max(0, i - 1))
        break
      case 'Home':
        event.preventDefault()
        setActive(0)
        break
      case 'End':
        event.preventDefault()
        setActive(last)
        break
      case 'Enter':
      case ' ':
        event.preventDefault()
        choose(active)
        break
      case 'Escape':
        // Stop it here: inside a dialog the same key would close the whole dialog.
        event.preventDefault()
        event.stopPropagation()
        setOpen(false)
        break
      case 'Tab':
        setOpen(false)
        break
      default: {
        if (event.key.length !== 1 || event.ctrlKey || event.metaKey || event.altKey) break
        const now = Date.now()
        const t = typed.current
        t.text = now - t.at > 700 ? event.key.toLowerCase() : t.text + event.key.toLowerCase()
        t.at = now
        // One letter moves on to the next match; a longer prefix may stay where it is.
        const from = t.text.length === 1 ? active + 1 : active
        for (let k = 0; k < options.length; k++) {
          const i = (from + k) % options.length
          if (options[i].label.toLowerCase().startsWith(t.text)) {
            setActive(i)
            break
          }
        }
      }
    }
  }

  return (
    <>
      <button
        ref={trigger}
        type="button"
        id={id}
        role="combobox"
        className={classNames('select-trigger', className)}
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-activedescendant={open ? optionId(active) : undefined}
        aria-label={aria['aria-label']}
        aria-describedby={aria['aria-describedby']}
        onClick={() => (open ? setOpen(false) : show())}
        onKeyDown={onKeyDown}
        onBlur={() => setOpen(false)}
      >
        {selected >= 0 ? options[selected].label : ''}
      </button>
      {open && place && (
        <ul
          ref={list}
          id={listId}
          role="listbox"
          className="select-list"
          style={place}
          aria-label={aria['aria-label']}
          // Named by a `<label htmlFor>`: the button already carries that name.
          aria-labelledby={aria['aria-label'] ? undefined : id}
          // Keeps focus on the button, so the click below does not first blur (and close) the list.
          onMouseDown={(event) => event.preventDefault()}
        >
          {options.map((option, i) => (
            // Pointer handlers only: the keyboard works on the button through aria-activedescendant.
            // eslint-disable-next-line jsx-a11y/click-events-have-key-events
            <li
              key={option.value}
              id={optionId(i)}
              role="option"
              aria-selected={i === selected}
              className={classNames('select-option', i === active && 'select-option--active')}
              onMouseMove={() => setActive(i)}
              onClick={() => choose(i)}
            >
              {option.label}
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
