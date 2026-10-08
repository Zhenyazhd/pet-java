import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../../api/client'
import type { AiScope } from '../../api/resumeTypes'
import { errorMessage, isAbortError } from '../abort'
import { readAiModel, writeAiModel } from './aiModels'
import { introducedProblem } from './limits'
import type { EditableResume } from './editable'
import { applyProposal, blankAbsentFields, parseProposal, type ProposalChange } from './proposal'
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

/** `answered`: a reply arrived. `applied`: it is on the sheet. `problem`: why the reply could not be used, if so. */
export type SendResult = {
  answered: boolean
  applied: boolean
  /** The CV was written but waits for Apply, because the sheet was edited meanwhile. */
  waiting?: boolean
  problem: string | null
}

const NOT_SENT: SendResult = { answered: false, applied: false, problem: null }

/** `shownAs` is what the chat displays for the sent text; `wholeCv` ignores the focus; `applyAtOnce` skips the Apply step. */
export type SendOptions = { shownAs?: string; wholeCv?: boolean; applyAtOnce?: boolean }

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
  // Two independent requests: the chat and "Create CV". Each has its own busy flag, abort and error,
  // so neither blocks the other.
  const [busy, setBusy] = useState(false)
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [undo, setUndo] = useState<{ snapshot: EditableResume; atEdit: number } | null>(null)

  const resumeRef = useRef(resume)
  resumeRef.current = resume
  const selectedRef = useRef(selected)
  selectedRef.current = selected
  const editsRef = useRef(edits)
  editsRef.current = edits
  const busyRefs = useRef({ chat: false, create: false })
  const aborts = useRef<{ chat: AbortController | null; create: AbortController | null }>({
    chat: null,
    create: null,
  })
  // Per channel: changing the vacancy resets the chat, but a CV being written from the profile does not depend on it.
  const generation = useRef({ chat: 0, create: 0 })
  const nextId = useRef(0)

  useEffect(
    () => () => {
      aborts.current.chat?.abort()
      aborts.current.create?.abort()
    },
    [],
  )

  const setModel = useCallback((next: string) => {
    setModelState(next)
    writeAiModel(next)
  }, [])

  const setItemState = useCallback((id: number, state: 'applied' | 'dismissed', problem?: string) => {
    setChat((items) =>
      items.map((item) =>
        item.id === id && item.role === 'assistant'
          ? { ...item, state, problem: problem ?? item.problem }
          : item,
      ),
    )
  }, [])

  const applyChange = useCallback(
    (change: ProposalChange): string | null => {
      const current = resumeRef.current
      if (!current) return 'There is no CV to apply this to.'
      const next = applyProposal(current, change)
      if (next === current) return 'This suggestion is for an entry that is no longer there.'
      const problem = introducedProblem(current, next)
      if (problem) return `Not applied, the sheet would become invalid: ${problem}`
      setUndo({ snapshot: current, atEdit: editsRef.current + 1 })
      patch((live) => applyProposal(live, change))
      return null
    },
    [patch],
  )

  const apply = useCallback(
    (id: number) => {
      const item = chat.find((entry) => entry.id === id)
      if (item?.role !== 'assistant' || !item.change) return
      const problem = applyChange(item.change)
      setItemState(id, problem ? 'dismissed' : 'applied', problem ?? undefined)
      if (!problem) announce('AI edit applied. Remember to save.')
    },
    [chat, applyChange, setItemState, announce],
  )

  const send = useCallback(
    async (instruction: string, options: SendOptions = {}): Promise<SendResult> => {
      const text = instruction.trim()
      const channel = options.applyAtOnce ? 'create' : 'chat'
      const setChannelBusy = channel === 'create' ? setCreating : setBusy
      // Only the chat shows its failures in the panel; Create reports its own through the result.
      const fail = (problem: string): SendResult => {
        if (channel === 'chat') setError(problem)
        return { ...NOT_SENT, problem }
      }
      if (!text || busyRefs.current[channel] || !resumeRef.current) return NOT_SENT
      if (blocked) return fail('The CV changed elsewhere. Reload the latest version before asking the AI.')
      busyRefs.current[channel] = true
      setChannelBusy(true)
      if (channel === 'chat') setError(null)
      const mine = generation.current[channel]
      const abort = new AbortController()
      aborts.current[channel] = abort
      const editsAtStart = editsRef.current
      // The chat remembers the conversation; a CV written from the profile starts from nothing.
      const history =
        channel === 'chat'
          ? chat.slice(-HISTORY_TURNS).map((item) => ({ role: item.role, content: item.text }))
          : []
      setChat((items) => [
        ...items,
        { id: ++nextId.current, role: 'user', text: options.shownAs ?? text },
      ])
      try {
        const outcome = await saveIfCurrent()
        if (abort.signal.aborted) return NOT_SENT
        const { selection, itemIndex } = options.wholeCv
          ? { selection: null, itemIndex: undefined }
          : resolveSelection(resumeRef.current, selectedRef.current)
        if (outcome === 'newer-edits' && selectedRef.current?.rowKey && !options.wholeCv) {
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
            // A CV written from the profile is the plain baseline, not tailored to a vacancy.
            ...(vacancyContext && !options.applyAtOnce ? { vacancyContext } : {}),
            model,
          },
          abort.signal,
        )
        if (mine !== generation.current[channel]) return NOT_SENT
        const parsed =
          result.proposed == null
            ? null
            : result.section === scope
              ? parseProposal(scope, rowKey, result.proposed)
              : ({ ok: false, error: 'The suggestion is for a different part of the CV than the one in focus.' } as const)
        // Blank at the source, so the reply is the same whether it lands at once or waits in a bubble for Apply.
        const change = parsed?.ok ? (options.applyAtOnce ? blankAbsentFields(parsed.change) : parsed.change) : null
        const parseProblem = parsed && !parsed.ok ? parsed.error : null
        // A CV made from the profile goes straight onto the sheet (Undo is there); other replies wait for Apply.
        // Not if the user edited while the AI was writing: that reply waits for an explicit Apply.
        const atOnce = options.applyAtOnce === true && editsRef.current === editsAtStart
        const refusal = atOnce && change ? applyChange(change) : null
        const applied = atOnce && change !== null && refusal === null
        const problem =
          refusal ??
          parseProblem ??
          (options.applyAtOnce && !change ? 'The AI did not return a CV.' : null)
        if (applied) announce('Your CV was written from your profile. Review it, then press Save.')
        setChat((items) => [
          ...items,
          {
            id: ++nextId.current,
            role: 'assistant',
            text: result.message || 'Done.',
            change,
            problem,
            state: applied ? 'applied' : refusal ? 'dismissed' : 'open',
          },
        ])
        const waiting = options.applyAtOnce === true && change !== null && !atOnce
        return {
          answered: true,
          applied,
          waiting,
          problem:
            problem ??
            (waiting ? 'You edited the sheet while it was being written, so the CV waits in the AI panel for Apply.' : null),
        }
      } catch (err) {
        if (mine !== generation.current[channel] || isAbortError(err)) return NOT_SENT
        return fail(errorMessage(err, 'The AI request failed'))
      } finally {
        if (mine === generation.current[channel]) {
          busyRefs.current[channel] = false
          aborts.current[channel] = null
          setChannelBusy(false)
        }
      }
    },
    [chat, blocked, vacancyContext, model, saveIfCurrent, applyChange, announce],
  )

  const undoLast = useCallback(() => {
    if (!undo) return
    patch((current) => ({ ...undo.snapshot, version: current.version }))
    setUndo(null)
    announce('AI edit undone.')
  }, [undo, patch, announce])

  const stop = useCallback(() => aborts.current.chat?.abort(), [])
  const stopCreate = useCallback(() => aborts.current.create?.abort(), [])

  /** Clears the conversation and stops a chat request. A "Create CV" run is not tied to it and keeps going. */
  const reset = useCallback(() => {
    generation.current.chat += 1
    aborts.current.chat?.abort()
    aborts.current.chat = null
    busyRefs.current.chat = false
    setBusy(false)
    setChat([])
    setError(null)
  }, [])

  return {
    chat,
    selected: resolveSelection(resume, selected).selection,
    select: setSelected,
    model,
    setModel,
    busy,
    creating,
    error,
    canUndo: undo !== null && undo.atEdit === edits,
    send,
    apply,
    dismiss: (id: number) => setItemState(id, 'dismissed'),
    undoLast,
    stop,
    stopCreate,
    reset,
  }
}
