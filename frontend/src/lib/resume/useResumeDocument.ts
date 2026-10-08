import { useCallback, useEffect, useRef, useState } from 'react'
import { api, isConflict } from '../../api/client'
import type { ResumeDocument } from '../../api/resumeTypes'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../abort'
import { toDocument, toEditable, type EditableResume } from './editable'
import { clearResumeDraft, decideDraft, readResumeDraft, writeResumeDraft } from './draft'
import { firstInvalidField } from './limits'

export class SaveConflictError extends Error {
  constructor() {
    super('The CV was changed in another tab or session. Reload the latest version, then redo your edits.')
    this.name = 'SaveConflictError'
  }
}

export class InvalidResumeError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'InvalidResumeError'
  }
}

export type SaveOutcome = 'unchanged' | 'saved' | 'newer-edits'

export function useResumeDocument() {
  const { user } = useAuth()
  const userId = user?.id ?? null

  const [resume, setResume] = useState<EditableResume | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [dirty, setDirty] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)
  const [restored, setRestored] = useState(false)
  const [staleDraft, setStaleDraft] = useState<ResumeDocument | null>(null)
  const [status, setStatus] = useState('')
  const [edits, setEdits] = useState(0)

  const resumeRef = useRef(resume)
  resumeRef.current = resume
  const dirtyRef = useRef(false)
  const editsRef = useRef(0)
  const savingRef = useRef(false)
  const loadSeq = useRef(0)
  const inFlight = useRef<{ promise: Promise<ResumeDocument>; edits: number } | null>(null)

  const markEdited = useCallback((isDirty: boolean) => {
    dirtyRef.current = isDirty
    setDirty(isDirty)
    editsRef.current += 1
    setEdits(editsRef.current)
  }, [])

  const load = useCallback(
    async (ignoreDraft = false): Promise<boolean> => {
      const seq = ++loadSeq.current
      setLoadError(null)
      try {
        const data = await api.getResume()
        if (seq !== loadSeq.current) return false
        const draft = ignoreDraft || userId === null ? null : readResumeDraft(userId)
        const decision = decideDraft(draft, data)
        if (decision === 'drop' && userId !== null) clearResumeDraft(userId)
        setResume(toEditable(decision === 'restore' && draft ? draft : data))
        setRestored(decision === 'restore')
        setStaleDraft(decision === 'stale' ? draft : null)
        markEdited(decision === 'restore')
        if (decision === 'restore') setStatus('Restored your unsaved draft.')
        return true
      } catch (err) {
        if (seq === loadSeq.current) setLoadError(errorMessage(err, 'Failed to load the CV'))
        return false
      }
    },
    [userId, markEdited],
  )

  useEffect(() => {
    void load()
    return () => {
      loadSeq.current += 1
    }
  }, [load])

  useEffect(() => {
    if (resume && dirty && userId !== null) writeResumeDraft(userId, toDocument(resume))
  }, [resume, dirty, userId])

  useEffect(() => {
    if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  const patch = useCallback(
    (updater: (current: EditableResume) => EditableResume) => {
      setResume((current) => (current ? updater(current) : current))
      markEdited(true)
      setStatus('')
      setStaleDraft(null)
    },
    [markEdited],
  )


  const saveIfCurrent = useCallback(async (): Promise<SaveOutcome> => {
    const current = resumeRef.current
    if (!current || !dirtyRef.current) return 'unchanged'
    const problem = firstInvalidField(current)
    if (problem) throw new InvalidResumeError(problem)
    if (!inFlight.current) {
      const promise = api.saveResume(toDocument(current)).finally(() => {
        inFlight.current = null
      })
      inFlight.current = { promise, edits: editsRef.current }
    }
    const { promise, edits: editsAtSave } = inFlight.current
    const loadsAtStart = loadSeq.current
    let saved: ResumeDocument
    try {
      saved = await promise
    } catch (err) {
      if (!isConflict(err)) throw err
      setConflict(true)
      throw new SaveConflictError()
    }
    if (loadsAtStart !== loadSeq.current) return 'unchanged'
    setResume((r) => (r ? { ...r, version: saved.version } : r))
    if (editsAtSave !== editsRef.current) return 'newer-edits'
    dirtyRef.current = false
    setDirty(false)
    setRestored(false)
    setStaleDraft(null)
    if (userId !== null) clearResumeDraft(userId)
    return 'saved'
  }, [userId])

  const save = useCallback(async () => {
    if (savingRef.current || conflict) return
    savingRef.current = true
    setSaving(true)
    setSaveError(null)
    try {
      const outcome = await saveIfCurrent()
      setStatus(
        outcome === 'unchanged'
          ? 'Nothing to save.'
          : outcome === 'newer-edits'
            ? 'Saved. You kept editing meanwhile: save again.'
            : 'Saved.',
      )
    } catch (err) {
      setSaveError(errorMessage(err, 'Save failed'))
    } finally {
      savingRef.current = false
      setSaving(false)
    }
  }, [saveIfCurrent, conflict])

  const discardAndReload = useCallback(async () => {
    if (!(await load(true))) return
    if (userId !== null) clearResumeDraft(userId)
    setConflict(false)
    setSaveError(null)
    setStatus('Loaded the latest saved CV.')
  }, [userId, load])

  const restoreStaleDraft = useCallback(() => {
    if (!staleDraft) return
    patch((current) => toEditable({ ...staleDraft, version: current.version }))
    setStaleDraft(null)
    setRestored(true)
  }, [staleDraft, patch])

  const dropStaleDraft = useCallback(() => {
    if (userId !== null) clearResumeDraft(userId)
    setStaleDraft(null)
  }, [userId])

  return {
    resume,
    loadError,
    dirty,
    saving,
    saveError,
    conflict,
    restored,
    staleDraft,
    status,
    edits,
    announce: setStatus,
    patch,
    save,
    saveIfCurrent,
    reload: load,
    discardAndReload,
    restoreStaleDraft,
    dropStaleDraft,
  }
}
