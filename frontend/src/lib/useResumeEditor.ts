import { FormEvent, useEffect, useRef, useState } from 'react'
import { api, isConflict } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { ChatItem } from '../components/resume/AiAssistPanel'
import { readAiModel, writeAiModel } from './aiModels'
import { compileResume } from './compileResume'
import { clearResumeDraft, pruneLegacyResumeDraft, readResumeDraft, writeResumeDraft } from './resumeDraft'
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
  const { user } = useAuth()
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
  /** Set when the server rejects a save as stale (edited elsewhere) — blocks further saves until reload. */
  const [conflict, setConflict] = useState(false)
  /**
   * True while the resume is being wholesale replaced (vacancy-context commit, or a
   * conflict reload). Save/preview/chat/match don't block each other — each dedups its
   * own save (see `saveInFlightRef`) — but none of them may run while the resume itself
   * is about to be swapped out from under them, and this op must wait for them too.
   */
  const [contextBusy, setContextBusy] = useState(false)
  /** Unsaved edits relative to last server sync — drives localStorage draft. */
  const dirtyRef = useRef(false)
  /** Bumped on local edits; in-flight saves must not overwrite newer UI state. */
  const editEpochRef = useRef(0)
  /** Shared in-flight save so concurrent ops (e.g. chat send + match check) join one
   *  network call instead of each saving and racing each other's version. */
  const saveInFlightRef = useRef<Promise<ResumeDocument> | null>(null)
  /** Synchronous per-action guards — React state alone can miss two clicks in one tick. */
  const savingRef = useRef(false)
  const previewingRef = useRef(false)
  const aiBusyRef = useRef(false)
  const matchingRef = useRef(false)
  const structuralLockRef = useRef(false)

  function anyActionBusy(): boolean {
    return savingRef.current || previewingRef.current || aiBusyRef.current || matchingRef.current
  }

  useEffect(() => {
    pruneLegacyResumeDraft()
    let cancelled = false
    let ackTimer: ReturnType<typeof setTimeout> | undefined
    ;(async () => {
      try {
        const data = await api.getResume()
        if (cancelled) return
        const incomingContext = takePrepareVacancyContext()
        const cached = readDraft()
        if (incomingContext) {
          dirtyRef.current = false
          clearDraft()
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
          const staleVersion = cached.version !== data.version
          setResume(normalizeResume({ ...cached, version: data.version }))
          setStatus(
            staleVersion
              ? 'Restored unsaved draft (aligned to latest server version)'
              : 'Restored unsaved draft',
          )
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
    writeDraft(resume)
  }, [resume, user])

  useEffect(() => {
    return () => {
      if (pdfUrl) URL.revokeObjectURL(pdfUrl)
    }
  }, [pdfUrl])

  function setAiModel(model: string) {
    setAiModelState(model)
    writeAiModel(model)
  }

  // Draft persistence is scoped to the logged-in user — an unscoped key would
  // resurrect a stranger's (or a previous account's) draft on this browser.
  function readDraft(): ResumeDocument | null {
    return user ? readResumeDraft(user.id) : null
  }

  function writeDraft(doc: ResumeDocument): void {
    if (user) writeResumeDraft(doc, user.id)
  }

  function clearDraft(): void {
    if (user) clearResumeDraft(user.id)
  }

  function markDirty() {
    dirtyRef.current = true
    editEpochRef.current += 1
    setStatus(null)
  }

  function markSynced() {
    dirtyRef.current = false
    clearDraft()
  }

  /**
   * Save `resume` if it has unsaved edits, then report whether it's still current.
   * Every op below saves before doing its real work (previewPdf, sendChat,
   * checkVacancyMatch), not just save() itself. Two safeguards keep those from
   * racing each other now that they no longer share one busy flag:
   *  - if nothing changed since the last sync, skip the network call entirely and
   *    reuse the already-current `resume` (also avoids a pointless version bump);
   *  - if a save is already in flight, join that same promise instead of firing a
   *    second one that would just lose the optimistic-concurrency check.
   */
  async function saveIfCurrent(): Promise<ResumeDocument | null> {
    if (!resume) return null
    if (!dirtyRef.current) return resume
    const epoch = editEpochRef.current
    if (!saveInFlightRef.current) {
      const resumeToSave = resume
      saveInFlightRef.current = api.saveResume(resumeToSave).finally(() => {
        saveInFlightRef.current = null
      })
    }
    let saved: ResumeDocument
    try {
      saved = await saveInFlightRef.current
    } catch (err) {
      if (isConflict(err)) {
        setConflict(true)
        throw new Error(
          'Resume was changed in another tab or session. Reload to get the latest version, then reapply your edits.',
        )
      }
      throw err
    }
    if (epoch !== editEpochRef.current) {
      dirtyRef.current = true
      return null
    }
    markSynced()
    setConflict(false)
    const normalized = normalizeResume(saved)
    setResume(normalized)
    return normalized
  }

  /** Discards local edits and reloads the server's current resume — the only way out of a conflict. */
  async function reloadResume() {
    if (structuralLockRef.current || anyActionBusy()) return
    structuralLockRef.current = true
    setContextBusy(true)
    setError(null)
    try {
      const data = await api.getResume()
      setResume(normalizeResume(data))
      dirtyRef.current = false
      clearDraft()
      setConflict(false)
      setStatus('Reloaded the latest resume from the server — your unsaved edits were discarded')
    } catch (err) {
      setError(errorMessage(err, 'Failed to reload resume'))
    } finally {
      structuralLockRef.current = false
      setContextBusy(false)
    }
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
    if (!resume || structuralLockRef.current || savingRef.current) return
    savingRef.current = true
    setSaving(true)
    setError(null)
    try {
      const saved = await saveIfCurrent()
      if (!saved) {
        setStatus('Saved, but you have newer local edits — save again')
        return
      }
      setStatus('Saved')
    } catch (err) {
      setError(errorMessage(err, 'Save failed'))
    } finally {
      setSaving(false)
      savingRef.current = false
    }
  }

  async function previewPdf() {
    if (!resume || structuralLockRef.current || previewingRef.current) return
    previewingRef.current = true
    setPreviewing(true)
    setError(null)
    try {
      const saved = await saveIfCurrent()
      if (!saved) {
        setStatus('PDF used a prior save — you have newer local edits')
      }
      const blob = await compileResume()
      const url = URL.createObjectURL(blob)
      setPdfUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev)
        return url
      })
    } catch (err) {
      setError(errorMessage(err, 'Compile failed'))
    } finally {
      setPreviewing(false)
      previewingRef.current = false
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
    if (!draft.trim() || !resume || structuralLockRef.current || aiBusyRef.current) return
    aiBusyRef.current = true
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
      aiBusyRef.current = false
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
    if (structuralLockRef.current || anyActionBusy()) return
    structuralLockRef.current = true
    setContextBusy(true)

    setChat([])
    setDraft('')
    setMatchReport(null)
    setMatchError(null)
    clearDraft()
    dirtyRef.current = false
    // Bump the epoch so any save() already in flight sees a stale epoch and
    // refuses to overwrite the resume we're about to reload here.
    editEpochRef.current += 1
    setError(null)
    try {
      const data = await api.getResume()
      setResume(normalizeResume(data))
      setConflict(false)
      setStatus('Vacancy context updated — draft and chat cleared')
    } catch (err) {
      setError(errorMessage(err, 'Failed to reload resume'))
    } finally {
      structuralLockRef.current = false
      setContextBusy(false)
    }
  }

  async function checkVacancyMatch() {
    if (!resume || !vacancyContext.trim() || structuralLockRef.current || matchingRef.current) return
    matchingRef.current = true
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
      matchingRef.current = false
    }
  }

  return {
    resume,
    selected,
    chat,
    draft,
    setDraft,
    aiBusy,
    previewing,
    saving,
    contextBusy,
    error,
    status,
    conflict,
    reloadResume,
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
