import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { CvSheet } from '../components/resume/CvSheet'
import { AiPanel } from '../components/resume/AiPanel'
import { MatchPanel } from '../components/resume/MatchPanel'
import { PdfDialog } from '../components/resume/PdfDialog'
import { ResumeToolbar } from '../components/resume/ResumeToolbar'
import { VacancyPicker } from '../components/resume/VacancyPicker'
import { Banner } from '../components/ui/Banner'
import { Button } from '../components/ui/Button'
import { PageHeader } from '../components/ui/PageHeader'
import { focusLabel } from '../lib/resume/focus'
import { sameTarget } from '../lib/resume/selection'
import { buildVacancyContext } from '../lib/vacancies/vacancyContext'
import { useDocumentTitle } from '../lib/useDocumentTitle'
import { useResumeAi } from '../lib/resume/useResumeAi'
import { useResumeDocument } from '../lib/resume/useResumeDocument'
import { useResumeJobs } from '../lib/resume/useResumeJobs'
import { useVacancies } from '../lib/vacancies/useVacancies'

function PageIntro() {
  return (
    <PageHeader
      eyebrow="Curriculum"
      title="Your CV"
      titleId="resume-title"
      lead="Edit the sheet in place, or point the AI at a block and apply what it proposes. Nothing changes until you press Apply, and nothing is stored until you save."
    />
  )
}

export function ResumePage() {
  useDocumentTitle('Resume')
  const doc = useResumeDocument()
  const { vacancies, loading: vacanciesLoading, error: vacanciesError } = useVacancies()
  const [params, setParams] = useSearchParams()

  const requestedId = Number(params.get('vacancy')) || null
  const vacancy = vacancies.find((candidate) => candidate.id === requestedId) ?? null
  const vacancyContext = vacancy ? buildVacancyContext(vacancy) : ''

  const ai = useResumeAi({
    resume: doc.resume,
    edits: doc.edits,
    vacancyContext,
    patch: doc.patch,
    saveIfCurrent: doc.saveIfCurrent,
    announce: doc.announce,
    blocked: doc.conflict,
  })
  const jobs = useResumeJobs(doc.saveIfCurrent, doc.conflict)
  const { reset: resetChat } = ai
  const { resetMatch } = jobs

  useEffect(() => {
    resetChat()
    resetMatch()
  }, [requestedId, resetChat, resetMatch])

  const { announce } = doc
  useEffect(() => {
    if (jobs.pdfNote) announce(jobs.pdfNote)
  }, [jobs.pdfNote, announce])

  function afterBannerAction(message: string) {
    announce(message)
    document.getElementById('resume-title')?.focus({ preventScroll: true })
  }

  function chooseVacancy(id: number | null) {
    setParams(id === null ? {} : { vacancy: String(id) }, { replace: true })
  }

  if (doc.loadError && !doc.resume) {
    return (
      <section className="page resume-page" aria-labelledby="resume-title">
        <PageIntro />
        <div className="stack">
          <Banner tone="error">{doc.loadError}</Banner>
          <div>
            <Button variant="outline" onClick={() => void doc.reload()}>
              Try again
            </Button>
          </div>
        </div>
      </section>
    )
  }

  if (!doc.resume) {
    return (
      <section className="page resume-page" aria-labelledby="resume-title">
        <PageIntro />
        <p className="status-text muted-line" role="status">
          Loading your CV…
        </p>
        <div className="cv-skeleton" aria-hidden="true" />
      </section>
    )
  }

  const resume = doc.resume
  const selected = ai.selected
  const conflictBlocked = doc.conflict

  return (
    <section className="page resume-page" aria-labelledby="resume-title">
      <PageIntro />

      <p className="status-text muted-line" role="status">
        {doc.status}
      </p>

      <ResumeToolbar
        locale={resume.locale}
        dirty={doc.dirty}
        saving={doc.saving}
        previewing={jobs.previewing}
        blocked={conflictBlocked}
        onLocaleChange={(locale) => doc.patch((current) => ({ ...current, locale }))}
        onPreview={() => void jobs.previewPdf()}
        onSave={() => void doc.save()}
      />

      <div className="stack">
        {conflictBlocked && (
          <Banner tone="error">
            <p>The CV was changed in another tab or session. Saving is paused.</p>
            <Button variant="outline" onClick={() => void doc.discardAndReload()}>
              Reload latest (discards your edits)
            </Button>
          </Banner>
        )}
        {doc.saveError && !conflictBlocked && <Banner tone="error">{doc.saveError}</Banner>}
        {doc.loadError && (
          <Banner tone="error">
            <p>{doc.loadError}</p>
            <Button variant="outline" onClick={() => void doc.discardAndReload()}>
              Try again
            </Button>
          </Banner>
        )}
        {doc.staleDraft && (
          <Banner tone="error">
            <p>
              A draft saved in this browser is based on an older version of the CV: it was saved
              elsewhere since. Restore the draft over the current version, or discard it.
            </p>
            <Button
              variant="outline"
              onClick={() => {
                doc.restoreStaleDraft()
                afterBannerAction('Draft restored over the current version.')
              }}
            >
              Restore draft
            </Button>{' '}
            <Button
              variant="ghost"
              onClick={() => {
                doc.dropStaleDraft()
                afterBannerAction('Draft discarded.')
              }}
            >
              Discard draft
            </Button>
          </Banner>
        )}
        {doc.restored && (
          <Banner tone="ok">
            <p>Restored an unsaved draft from this browser.</p>
            <Button variant="ghost" onClick={() => void doc.discardAndReload()}>
              Discard all unsaved changes
            </Button>
          </Banner>
        )}
        {jobs.pdfFailure && (
          <Banner tone="error">
            <p>{jobs.pdfFailure.latexLog ? 'The PDF could not be built (LaTeX error):' : jobs.pdfFailure.message}</p>
            {jobs.pdfFailure.latexLog && <pre className="log-block" tabIndex={0} role="region" aria-label="LaTeX log">{jobs.pdfFailure.message}</pre>}
            <Button variant="ghost" onClick={jobs.dismissPdfFailure}>
              Dismiss
            </Button>
          </Banner>
        )}
      </div>

      <div className="resume-layout">
        <CvSheet
          resume={resume}
          selected={selected}
          onSelect={(target) => ai.select(sameTarget(selected, target) ? null : target)}
          onPatch={doc.patch}
        />
        <div className="resume-side">
          <VacancyPicker
            vacancies={vacancies}
            loading={vacanciesLoading}
            error={vacanciesError}
            value={vacancy?.id ?? null}
            onChange={chooseVacancy}
          />
          <AiPanel
            focus={focusLabel(resume, selected)}
            focused={selected !== null}
            chat={ai.chat}
            model={ai.model}
            busy={ai.busy}
            error={ai.error}
            canUndo={ai.canUndo}
            hasVacancy={vacancy !== null}
            holdSend={
              conflictBlocked
                ? 'Reload the latest CV to continue.'
                : requestedId !== null && vacanciesLoading
                  ? 'Loading the vacancy…'
                  : null
            }
            onWholeCv={() => ai.select(null)}
            onModelChange={ai.setModel}
            onSend={ai.send}
            onStop={ai.stop}
            onApply={ai.apply}
            onDismiss={ai.dismiss}
            onUndo={ai.undoLast}
          />
        </div>
      </div>

      <MatchPanel
        hasVacancy={vacancy !== null}
        matching={jobs.matching}
        blocked={conflictBlocked}
        report={jobs.report}
        error={jobs.matchError}
        note={jobs.matchNote}
        onCheck={() => void jobs.checkMatch(vacancyContext, vacancy?.id ?? null)}
      />

      {jobs.pdfUrl && <PdfDialog url={jobs.pdfUrl} onClose={jobs.closePdf} />}
    </section>
  )
}
