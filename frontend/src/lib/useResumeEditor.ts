import { FormEvent, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import type { ChatItem } from '../components/resume/AiAssistPanel'
import { readAiModel, writeAiModel } from './aiModels'
import { clearResumeDraft, readResumeDraft, writeResumeDraft } from './resumeDraft'
import { applyAiProposed } from './resumeEdits'
import { resolveLocale } from './resumeLocale'
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
  const [busy, setBusy] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [status, setStatus] = useState<string | null>(null)
  const [pdfUrl, setPdfUrl] = useState<string | null>(null)
  const [vacancyContext, setVacancyContext] = useState('')
  const [contextOpen, setContextOpen] = useState(false)
  const [contextDraft, setContextDraft] = useState('')
  const [aiModel, setAiModelState] = useState(readAiModel)
  const [matching, setMatching] = useState(false)
  const [matchReport, setMatchReport] = useState<MatchResponse | null>(null)
  const [matchError, setMatchError] = useState<string | null>(null)
  /** Unsaved edits relative to last server sync — drives localStorage draft. */
  const dirtyRef = useRef(false)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const data = await api.getResume()
        if (cancelled) return
        const cached = readResumeDraft()
        if (cached) {
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
    setStatus(null)
  }

  function markSynced() {
    dirtyRef.current = false
    clearResumeDraft()
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
    if (!resume) return
    setSaving(true)
    setError(null)
    try {
      const saved = await api.saveResume(resume)
      setResume(normalizeResume(saved))
      markSynced()
      setStatus('Saved')
    } catch (err) {
      setError(errorMessage(err, 'Save failed'))
    } finally {
      setSaving(false)
    }
  }

  async function previewPdf() {
    if (!resume) return
    setBusy(true)
    setError(null)
    try {
      await api.saveResume(resume)
      markSynced()
      const blob = await api.compileResume()
      const url = URL.createObjectURL(blob)
      setPdfUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev)
        return url
      })
    } catch (err) {
      setError(errorMessage(err, 'Compile failed'))
    } finally {
      setBusy(false)
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
    if (!draft.trim() || !resume) return
    const instruction = draft.trim()
    setDraft('')
    const history: ChatTurn[] = chat.slice(-12).map((item) => ({
      role: item.role,
      content: item.text,
    }))
    setChat((items) => [...items, { role: 'user', text: instruction }])
    setBusy(true)
    setError(null)
    const scope = selected?.section ?? 'all'
    const itemIndex = selected?.itemIndex
    try {
      await api.saveResume(resume)
      markSynced()
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
      setBusy(false)
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
    if (!changed) return

    setChat([])
    setDraft('')
    setMatchReport(null)
    setMatchError(null)
    clearResumeDraft()
    dirtyRef.current = false
    setError(null)
    try {
      const data = await api.getResume()
      setResume(normalizeResume(data))
      setStatus('Vacancy context updated — draft and chat cleared')
    } catch (err) {
      setError(errorMessage(err, 'Failed to reload resume'))
    }
  }

  async function checkVacancyMatch() {
    if (!resume || !vacancyContext.trim() || matching || busy) return
    setMatching(true)
    setMatchError(null)
    setMatchReport(null)
    setStatus(null)
    try {
      await api.saveResume(resume)
      markSynced()
      const result = await api.matchResume(vacancyContext)
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
    }
  }

  return {
    resume,
    selected,
    chat,
    draft,
    setDraft,
    busy,
    saving,
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
