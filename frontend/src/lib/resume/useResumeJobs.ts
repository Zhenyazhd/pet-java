import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../../api/client'
import type { MatchResponse, ReadabilityReport } from '../../api/resumeTypes'
import { errorMessage, isAbortError } from '../abort'
import { SaveConflictError, type SaveOutcome } from './useResumeDocument'
import { waitForJob } from '../waitForJob'

const PDF_MAX_WAIT_MS = 2 * 60 * 1000
const MATCH_MAX_WAIT_MS = 5 * 60 * 1000

export type PdfFailure = { message: string; latexLog: boolean }

export function useResumeJobs(saveIfCurrent: () => Promise<SaveOutcome>, blocked: boolean) {
  const [pdfUrl, setPdfUrl] = useState<string | null>(null)
  const [previewing, setPreviewing] = useState(false)
  const [pdfFailure, setPdfFailure] = useState<PdfFailure | null>(null)
  const [pdfNote, setPdfNote] = useState<string | null>(null)
  const [matching, setMatching] = useState(false)
  const [report, setReport] = useState<MatchResponse | null>(null)
  const [matchError, setMatchError] = useState<string | null>(null)
  const [matchNote, setMatchNote] = useState<string | null>(null)
  const [checking, setChecking] = useState(false)
  const [readability, setReadability] = useState<ReadabilityReport | null>(null)
  const [readabilityError, setReadabilityError] = useState<string | null>(null)
  const [readabilityNote, setReadabilityNote] = useState<string | null>(null)

  const previewingRef = useRef(false)
  const matchingRef = useRef(false)
  const pdfAbort = useRef<AbortController | null>(null)
  const matchAbort = useRef<AbortController | null>(null)
  const matchRun = useRef(0)
  const checkingRef = useRef(false)
  const readabilityAbort = useRef<AbortController | null>(null)

  useEffect(
    () => () => {
      pdfAbort.current?.abort()
      matchAbort.current?.abort()
      readabilityAbort.current?.abort()
    },
    [],
  )

  useEffect(() => {
    if (!pdfUrl) return
    return () => URL.revokeObjectURL(pdfUrl)
  }, [pdfUrl])

  /** Builds the PDF of the saved resume; the match and the readability check work on that PDF. */
  const buildPdf = useCallback(async (signal: AbortSignal) => {
    const job = await waitForJob(await api.startCompile(signal), api.getCompileJob, {
      maxWaitMs: PDF_MAX_WAIT_MS,
      tooLongMessage: 'The PDF build is taking too long. Try again in a minute.',
      signal,
    })
    if (job.status !== 'DONE') {
      throw new PdfBuildError(job.error?.message ?? 'The PDF build failed', job.error?.code === 'compile_error')
    }
    return job
  }, [])

  const previewPdf = useCallback(async () => {
    if (previewingRef.current || blocked) return
    previewingRef.current = true
    setPreviewing(true)
    setPdfFailure(null)
    setPdfNote(null)
    const abort = new AbortController()
    pdfAbort.current = abort
    try {
      const outcome = await saveIfCurrent()
      if (outcome === 'newer-edits') setPdfNote('The PDF shows your last save, without the edits made since.')
      const job = await buildPdf(abort.signal)
      setPdfUrl(URL.createObjectURL(await api.getCompiledPdf(job.id, abort.signal)))
    } catch (err) {
      if (isAbortError(err)) return
      setPdfFailure({
        message: errorMessage(err, 'The PDF build failed'),
        latexLog: err instanceof PdfBuildError && err.latexLog,
      })
    } finally {
      previewingRef.current = false
      setPreviewing(false)
    }
  }, [saveIfCurrent, blocked, buildPdf])

  const checkMatch = useCallback(
    async (vacancyContext: string, vacancyId: number | null) => {
      if (matchingRef.current || blocked || !vacancyContext) return
      matchingRef.current = true
      const run = ++matchRun.current
      setMatching(true)
      setMatchError(null)
      setMatchNote(null)
      setReport(null)
      const abort = new AbortController()
      matchAbort.current = abort
      try {
        const outcome = await saveIfCurrent()
        // Starting a run costs minutes and tokens: not for a vacancy the user has already left.
        if (abort.signal.aborted) return
        if (outcome === 'newer-edits') setMatchNote('The score is for your last save, without the edits made since.')
        const pdf = await buildPdf(abort.signal)
        if (run !== matchRun.current) return
        const job = await waitForJob(await api.startMatch(vacancyContext, vacancyId, pdf.id, abort.signal), api.getMatchJob, {
          maxWaitMs: MATCH_MAX_WAIT_MS,
          tooLongMessage: 'The match check is taking too long. Try again in a few minutes.',
          signal: abort.signal,
        })
        if (run !== matchRun.current) return
        if (job.status !== 'DONE' || !job.result) {
          throw new Error(job.error?.message ?? 'The match check failed')
        }
        setReport(job.result)
      } catch (err) {
        if (run !== matchRun.current || isAbortError(err)) return
        setMatchError(
          err instanceof SaveConflictError ? err.message : errorMessage(err, 'The match check failed'),
        )
      } finally {
        if (run === matchRun.current) {
          matchingRef.current = false
          setMatching(false)
        }
      }
    },
    [saveIfCurrent, blocked, buildPdf],
  )

  const checkReadability = useCallback(async () => {
    if (checkingRef.current || blocked) return
    checkingRef.current = true
    setChecking(true)
    setReadabilityError(null)
    setReadabilityNote(null)
    setReadability(null)
    const abort = new AbortController()
    readabilityAbort.current = abort
    try {
      const outcome = await saveIfCurrent()
      if (abort.signal.aborted) return
      if (outcome === 'newer-edits') setReadabilityNote('The check is for your last save, without the edits made since.')
      const pdf = await buildPdf(abort.signal)
      const job = await waitForJob(await api.startReadability(pdf.id, abort.signal), api.getReadabilityJob, {
        maxWaitMs: PDF_MAX_WAIT_MS,
        tooLongMessage: 'The check is taking too long. Try again in a minute.',
        signal: abort.signal,
      })
      if (job.status !== 'DONE' || !job.result) throw new Error(job.error?.message ?? 'The check failed')
      setReadability(job.result)
    } catch (err) {
      if (isAbortError(err)) return
      setReadabilityError(
        err instanceof SaveConflictError ? err.message : errorMessage(err, 'The check failed'),
      )
    } finally {
      checkingRef.current = false
      setChecking(false)
    }
  }, [saveIfCurrent, blocked, buildPdf])

  const resetMatch = useCallback(() => {
    matchRun.current += 1
    matchAbort.current?.abort()
    matchingRef.current = false
    setMatching(false)
    setReport(null)
    setMatchError(null)
    setMatchNote(null)
  }, [])

  return {
    pdfUrl,
    previewing,
    pdfFailure,
    pdfNote,
    previewPdf,
    closePdf: () => {
      setPdfUrl(null)
      setPdfNote(null)
    },
    dismissPdfFailure: () => setPdfFailure(null),
    matching,
    report,
    matchError,
    matchNote,
    checkMatch,
    resetMatch,
    checking,
    readability,
    readabilityError,
    readabilityNote,
    checkReadability,
  }
}

class PdfBuildError extends Error {
  readonly latexLog: boolean

  constructor(message: string, latexLog: boolean) {
    super(message)
    this.latexLog = latexLog
  }
}
