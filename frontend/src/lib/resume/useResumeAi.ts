import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../../api/client'
import type { AiScope } from '../../api/resumeTypes'
import { errorMessage, isAbortError } from '../abort'
import { readAiModel, writeAiModel } from './aiModels'
import { introducedProblem } from './limits'
import type { EditableResume } from './editable'
import { applyProposal, parseProposal, type ProposalChange } from './proposal'
import { resolveSelection, type Selection } from './selection'
import type { SaveOutcome } from './useResumeDocument'

const HISTORY_TURNS = 12

export type ChatItem =
  | { id: number; role: 'user'; text: string }
  | {
      id: number
      role: 'assistant'
      text: string
      change: ProposalChange | null
      problem: string | null
      state: 'open' | 'applied' | 'dismissed'
    }

type Options = {
  resume: EditableResume | null
  edits: number
  vacancyContext: string
  patch: (updater: (current: EditableResume) => EditableResume) => void
  saveIfCurrent: () => Promise<SaveOutcome>
  announce: (message: string) => void
  blocked: boolean
}

export function useResumeAi({
  resume,
  edits,
  vacancyContext,
  patch,
  saveIfCurrent,
  announce,
  blocked,
}: Options) {
  const [chat, setChat] = useState<ChatItem[]>([])
  const [selected, setSelected] = useState<Selection | null>(null)
  const [model, setModelState] = useState(readAiModel)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [undo, setUndo] = useState<{ snapshot: EditableResume; atEdit: number } | null>(null)

  const resumeRef = useRef(resume)
  resumeRef.current = resume
  const selectedRef = useRef(selected)
  selectedRef.current = selected
  const busyRef = useRef(false)
  const abortRef = useRef<AbortController | null>(null)
  const generation = useRef(0)
  const nextId = useRef(0)

  useEffect(() => () => abortRef.current?.abort(), [])

  const setModel = useCallback((next: string) => {
    setModelState(next)
    writeAiModel(next)
  }, [])

  const send = useCallback(
    async (instruction: string): Promise<boolean> => {
      const text = instruction.trim()
      if (!text || busyRef.current || !resumeRef.current) return false
      if (blocked) {
        setError('The CV changed elsewhere. Reload the latest version before asking the AI.')
        return false
      }
      busyRef.current = true
      setBusy(true)
      setError(null)
      const mine = generation.current
      const abort = new AbortController()
      abortRef.current = abort
      const history = chat.slice(-HISTORY_TURNS).map((item) => ({ role: item.role, content: item.text }))
      setChat((items) => [...items, { id: ++nextId.current, role: 'user', text }])
      try {
        const outcome = await saveIfCurrent()
        const { selection, itemIndex } = resolveSelection(resumeRef.current, selectedRef.current)
        if (outcome === 'newer-edits' && selectedRef.current?.rowKey) {
          throw new Error('You edited while the CV was saving. Send the message again.')
        }
        const scope: AiScope = selection?.section ?? 'all'
        const rowKey = selection?.rowKey ?? null
        const result = await api.suggestResumeSection(
          {
            section: scope,
            instruction: text,
            ...(itemIndex === undefined ? {} : { itemIndex }),
            ...(history.length > 0 ? { history } : {}),
            ...(vacancyContext ? { vacancyContext } : {}),
            model,
          },
          abort.signal,
        )
        if (mine !== generation.current) return false
        const parsed =
          result.proposed == null
            ? null
            : result.section === scope
              ? parseProposal(scope, rowKey, result.proposed)
              : ({ ok: false, error: 'The suggestion is for a different part of the CV than the one in focus.' } as const)
        setChat((items) => [
          ...items,
          {
            id: ++nextId.current,
            role: 'assistant',
            text: result.message || 'Done.',
            change: parsed?.ok ? parsed.change : null,
            problem: parsed && !parsed.ok ? parsed.error : null,
            state: 'open',
          },
        ])
        return true
      } catch (err) {
        if (mine !== generation.current || isAbortError(err)) return false
        setError(errorMessage(err, 'The AI request failed'))
        return false
      } finally {
        if (mine === generation.current) {
          busyRef.current = false
          abortRef.current = null
          setBusy(false)
        }
      }
    },
    [chat, blocked, vacancyContext, model, saveIfCurrent],
  )

  const setItemState = useCallback((id: number, state: 'applied' | 'dismissed', problem?: string) => {
    setChat((items) =>
      items.map((item) =>
        item.id === id && item.role === 'assistant'
          ? { ...item, state, problem: problem ?? item.problem }
          : item,
      ),
    )
  }, [])

  const apply = useCallback(
    (id: number) => {
      const item = chat.find((entry) => entry.id === id)
      if (!resume || item?.role !== 'assistant' || !item.change) return
      const change = item.change
      const next = applyProposal(resume, change)
      if (next === resume) {
        setItemState(id, 'dismissed', 'This suggestion is for an entry that is no longer there.')
        return
      }
      const problem = introducedProblem(resume, next)
      if (problem) {
        setItemState(id, 'dismissed', `Not applied, the sheet would become invalid: ${problem}`)
        return
      }
      setUndo({ snapshot: resume, atEdit: edits + 1 })
      patch((current) => applyProposal(current, change))
      setItemState(id, 'applied')
      announce('AI edit applied. Remember to save.')
    },
    [chat, resume, edits, patch, setItemState, announce],
  )

  const undoLast = useCallback(() => {
    if (!undo) return
    patch((current) => ({ ...undo.snapshot, version: current.version }))
    setUndo(null)
    announce('AI edit undone.')
  }, [undo, patch, announce])

  const stop = useCallback(() => abortRef.current?.abort(), [])

  const reset = useCallback(() => {
    generation.current += 1
    abortRef.current?.abort()
    abortRef.current = null
    busyRef.current = false
    setBusy(false)
    setChat([])
    setError(null)
    setUndo(null)
  }, [])

  return {
    chat,
    selected: resolveSelection(resume, selected).selection,
    select: setSelected,
    model,
    setModel,
    busy,
    error,
    canUndo: undo !== null && undo.atEdit === edits,
    send,
    apply,
    dismiss: (id: number) => setItemState(id, 'dismissed'),
    undoLast,
    stop,
    reset,
  }
}
