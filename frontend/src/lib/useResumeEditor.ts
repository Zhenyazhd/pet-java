import { FormEvent, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import type { ChatItem } from '../components/resume/AiAssistPanel'
import { readAiModel, writeAiModel } from './aiModels'
import { clearResumeDraft, readResumeDraft, writeResumeDraft } from './resumeDraft'
import { applyAiProposed } from './resumeEdits'
import { resolveLocale } from './resumeLocale'
import {
  acknowledgePrepareVacancyContext,
  takePrepareVacancyContext,
} from './vacancyPrepare'
import type {
  MatchResponse,
  ResumeDocument,
  ResumeSection,
  Selection,
  SuggestResponse,
  ChatTurn,
} from '../types/resume'

function errorMessage(err: unknown, fallback: string): string {
  return err instanceof Error ? err.message : fallback
}

function normalizeResume(data: ResumeDocument): ResumeDocument {
  return { ...data, locale: resolveLocale(data.locale) }
}

export function useResumeEditor() {
  const [resume, setResume] = useState<ResumeDocument | null>(null)
  const [selected, setSelected] = useState<Selection | null>(null)
  const [chat, setChat] = useState<ChatItem[]>([])
  const [draft, setDraft] = useState('')
  const [aiBusy, setAiBusy] = useState(false)
  const [previewing, setPreviewing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [status, setStatus] = useState<string | null>(null)
  const [pdfUrl, setPdfUrl] = useState<string | null>(null)
  const [vacancyContext, setVacancyContext] = useState('')
  const [vacancyId, setVacancyId] = useState<number | null>(null)
  const [contextOpen, setContextOpen] = useState(false)
  const [contextDraft, setContextDraft] = useState('')
  const [aiModel, setAiModelState] = useState(readAiModel)
  const [matching, setMatching] = useState(false)
  const [matchReport, setMatchReport] = useState<MatchResponse | null>(null)
  const [matchError, setMatchError] = useState<string | null>(null)
  /** Unsaved edits relative to last server sync — drives localStorage draft. */
  const dirtyRef = useRef(false)
  /** Bumped on local edits; in-flight saves must not overwrite newer UI state. */
  const editEpochRef = useRef(0)
  /** Synchronous mutex — React state alone can miss two clicks in one tick. */
  const opsLockRef = useRef(false)

  function isOpsLocked() {
    return opsLockRef.current || saving || previewing || aiBusy || matching
  }

  function beginOp(): boolean {
    if (isOpsLocked()) return false
    opsLockRef.current = true
    return true
  }

  function endOp() {
    opsLockRef.current = false
  }

  useEffect(() => {
    let cancelled = false
    let ackTimer: ReturnType<typeof setTimeout> | undefined
    ;(async () => {
      try {
        const data = await api.getResume()
        if (cancelled) return
        const incomingContext = takePrepareVacancyContext()
        const cached = readResumeDraft()
        if (incomingContext) {
          dirtyRef.current = false
          clearResumeDraft()
          setResume(normalizeResume(data))
          setVacancyContext(incomingContext.context)
          setVacancyId(incomingContext.vacancyId)
          setContextDraft(incomingContext.context)
          setContextOpen(true)
          setChat([])
          setDraft('')
          setMatchReport(null)
          setMatchError(null)
          setStatus('Vacancy context loaded from Prepare CV — chat cleared')
          // Defer clear so React Strict Mode remount can re-read the handoff.
          ackTimer = setTimeout(() => acknowledgePrepareVacancyContext(), 0)
        } else if (cached) {
          dirtyRef.current = true
          setResume(normalizeResume(cached))
          setStatus('Restored unsaved draft')
        } else {
          dirtyRef.current = false
          setResume(normalizeResume(data))
        }
      } catch (err) {
        if (!cancelled) setError(errorMessage(err, 'Failed to load resume'))
      }
    })()
    return () => {
      cancelled = true
      if (ackTimer) clearTimeout(ackTimer)
    }
  }, [])

  useEffect(() => {
    if (!resume || !dirtyRef.current) return
    writeResumeDraft(resume)
  }, [resume])

  useEffect(() => {
    return () => {
      if (pdfUrl) URL.revokeObjectURL(pdfUrl)
    }
  }, [pdfUrl])

  function setAiModel(model: string) {
    setAiModelState(model)
    writeAiModel(model)
  }

  function markDirty() {
    dirtyRef.current = true
    editEpochRef.current += 1
    setStatus(null)
  }

  function markSynced() {
    dirtyRef.current = false
    clearResumeDraft()
  }

  /**
   * Save `resume`, then report whether it's still current. Every op below
   * saves before doing its real work; if a newer edit landed meanwhile the
   * save result is stale and must not overwrite in-flight edits.
   */
  async function saveIfCurrent(): Promise<ResumeDocument | null> {
    if (!resume) return null
    const epoch = editEpochRef.current
    const saved = await api.saveResume(resume)
    if (epoch !== editEpochRef.current) {
      dirtyRef.current = true
      return null
    }
    markSynced()
    return saved
  }

  function select(section: ResumeSection, itemIndex?: number, e?: { stopPropagation(): void }) {
    e?.stopPropagation()
    setSelected(itemIndex === undefined ? { section } : { section, itemIndex })
    setError(null)
  }

  function clearFocus() {
    setSelected(null)
  }

  function patch(updater: (current: ResumeDocument) => ResumeDocument) {
    markDirty()
    setResume((current) => (current ? updater(current) : current))
  }

  async function save() {
    if (!resume || !beginOp()) return
    setSaving(true)
    setError(null)
    try {
      const saved = await saveIfCurrent()
      if (!saved) {
        setStatus('Saved, but you have newer local edits — save again')
        return
      }
      setResume(normalizeResume(saved))
      setStatus('Saved')
    } catch (err) {
      setError(errorMessage(err, 'Save failed'))
    } finally {
      setSaving(false)
      endOp()
    }
  }

  async function previewPdf() {
    if (!resume || !beginOp()) return
    setPreviewing(true)
    setError(null)
    try {
      const saved = await saveIfCurrent()
      if (!saved) {
        setStatus('PDF used a prior save — you have newer local edits')
      }
      const blob = await api.compileResume()
      const url = URL.createObjectURL(blob)
      setPdfUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev)
        return url
      })
    } catch (err) {
      setError(errorMessage(err, 'Compile failed'))
    } finally {
      setPreviewing(false)
      endOp()
    }
  }

  function closePdf() {
    setPdfUrl((prev) => {
      if (prev) URL.revokeObjectURL(prev)
      return null
    })
  }

  async function sendChat(e: FormEvent) {
    e.preventDefault()
    if (!draft.trim() || !resume || !beginOp()) return
    const instruction = draft.trim()
    setDraft('')
    const history: ChatTurn[] = chat.slice(-12).map((item) => ({
      role: item.role,
      content: item.text,
    }))
    setChat((items) => [...items, { role: 'user', text: instruction }])
    setAiBusy(true)
    setError(null)
    const scope = selected?.section ?? 'all'
    const itemIndex = selected?.itemIndex
    try {
      await saveIfCurrent()
      const result: SuggestResponse = await api.suggestResumeSection(
        scope,
        instruction,
        itemIndex,
        history,
        vacancyContext,
        aiModel,
      )
      setChat((items) => [
        ...items,
        {
          role: 'assistant',
          text: result.message || 'OK',
          proposed: result.proposed ?? null,
          section: result.section || scope,
          itemIndex: result.itemIndex ?? itemIndex ?? null,
        },
      ])
    } catch (err) {
      setError(errorMessage(err, 'AI request failed'))
    } finally {
      setAiBusy(false)
      endOp()
    }
  }

  function applySuggestion(item: Extract<ChatItem, { role: 'assistant' }>) {
    if (!resume || item.proposed == null) return
    markDirty()
    setResume(applyAiProposed(resume, item.section, item.itemIndex, item.proposed))
    setStatus('AI edit applied — remember to Save')
  }

  function setLocale(locale: 'fr' | 'en') {
    patch((r) => ({ ...r, locale }))
  }

  function openContextModal() {
    setContextDraft(vacancyContext)
    setContextOpen(true)
  }

  function closeContextModal() {
    setContextOpen(false)
  }

  async function commitVacancyContext(next: string) {
    const normalized = next.trim()
    const changed = normalized !== vacancyContext.trim()
    setVacancyContext(normalized)
    setContextDraft(normalized)
    setContextOpen(false)
    if (!normalized) {
      setVacancyId(null)
    }
    if (!changed) return
    if (!beginOp()) return

    setChat([])
    setDraft('')
    setMatchReport(null)
    setMatchError(null)
    clearResumeDraft()
    dirtyRef.current = false
    // Bump the epoch so any save() already in flight sees a stale epoch and
    // refuses to overwrite the resume we're about to reload here.
    editEpochRef.current += 1
    setError(null)
    try {
      const data = await api.getResume()
      setResume(normalizeResume(data))
      setStatus('Vacancy context updated — draft and chat cleared')
    } catch (err) {
      setError(errorMessage(err, 'Failed to reload resume'))
    } finally {
      endOp()
    }
  }

  async function checkVacancyMatch() {
    if (!resume || !vacancyContext.trim() || !beginOp()) return
    setMatching(true)
    setMatchError(null)
    setMatchReport(null)
    setStatus(null)
    try {
      await saveIfCurrent()
      const result = await api.matchResume(
        vacancyContext,
        vacancyId ?? undefined,
      )
      setMatchReport(result)
      setMatchError(null)
      setStatus(`Vacancy match: ${result.averageScore}/100`)
    } catch (err) {
      const message = errorMessage(err, 'Match check failed')
      setMatchReport(null)
      setMatchError(message)
      setStatus(null)
    } finally {
      setMatching(false)
      endOp()
    }
  }

  const opsLocked = isOpsLocked()

  return {
    resume,
    selected,
    chat,
    draft,
    setDraft,
    aiBusy,
    previewing,
    saving,
    opsLocked,
    error,
    status,
    pdfUrl,
    vacancyContext,
    contextOpen,
    contextDraft,
    setContextDraft,
    aiModel,
    setAiModel,
    matching,
    matchReport,
    matchError,
    setMatchError,
    setLocale,
    select,
    clearFocus,
    patch,
    save,
    previewPdf,
    closePdf,
    sendChat,
    applySuggestion,
    openContextModal,
    closeContextModal,
    commitVacancyContext,
    checkVacancyMatch,
  }
}
