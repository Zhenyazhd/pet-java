import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { CreateCvCard } from '../components/resume/CreateCvCard'
import { CvSheet } from '../components/resume/CvSheet'
import { AiPanel } from '../components/resume/AiPanel'
import { MatchPanel } from '../components/resume/MatchPanel'
import { PdfDialog } from '../components/resume/PdfDialog'
import { ResumeToolbar } from '../components/resume/ResumeToolbar'
import { VacancyPicker } from '../components/resume/VacancyPicker'
import { Banner } from '../components/ui/Banner'
import { Button } from '../components/ui/Button'
import { PageHeader } from '../components/ui/PageHeader'
import {
  CREATE_FROM_PROFILE_INSTRUCTION,
  CREATE_FROM_PROFILE_SHOWN_AS,
  shouldOfferCreate,
} from '../lib/resume/createFromProfile'
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
      lead="Edit the sheet in place, or point the AI at a block. Its suggestions wait for your Apply, and nothing is stored until you save."
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
  const [createFailure, setCreateFailure] = useState<string | null>(null)
  useEffect(() => {
    if (jobs.pdfNote) announce(jobs.pdfNote)
  }, [jobs.pdfNote, announce])

  async function createCv() {
    setCreateFailure(null)
    const result = await ai.send(CREATE_FROM_PROFILE_INSTRUCTION, {
      shownAs: CREATE_FROM_PROFILE_SHOWN_AS,
      wholeCv: true,
      applyAtOnce: true,
    })
    // No reply and no reason means the user stopped it or changed vacancy: nothing to report.
    if (!result.applied && (result.answered || result.problem)) {
      setCreateFailure(
        result.problem
          ? result.waiting
            ? result.problem
            : `Could not create the CV: ${result.problem}`
          : 'The CV was not put on the sheet. Check the AI panel for the reply.',
      )
    }
  }

  // The pressed banner button unmounts: say what happened and keep the keyboard on the page.
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
  const sendHold = conflictBlocked
    ? 'Reload the latest CV to continue.'
    : requestedId !== null && vacanciesLoading
      ? 'Loading the vacancy…'
      : null

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
              This browser holds edits you made to an older version of your CV. The CV has been saved
              again since, for example in another tab, so putting these edits on the sheet replaces
              that newer version.
            </p>
            <Button
              variant="outline"
              onClick={() => {
                doc.restoreStaleDraft()
                afterBannerAction('Your edits are on the sheet.')
              }}
            >
              Put my edits on the sheet
            </Button>{' '}
            <Button
              variant="ghost"
              onClick={() => {
                doc.dropStaleDraft()
                afterBannerAction('Edits deleted.')
              }}
            >
              Delete these edits
            </Button>
          </Banner>
        )}
        {doc.restored && (
          <Banner tone="ok">
            <p>
              You did not save your last edits, so they were kept in this browser and are back on the
              sheet. Press Save to keep them, or discard them to return to the saved CV.
            </p>
            <Button variant="ghost" onClick={() => void doc.discardAndReload()}>
              Discard edits and load the saved CV
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

      {/* Kept while a request or its failure is pending: the save that precedes the request moves the version. */}
      {(shouldOfferCreate(resume.version) || ai.creating || createFailure) && (
        <CreateCvCard
          creating={ai.creating}
          onCancel={ai.stopCreate}
          replacesEdits={doc.dirty}
          hold={conflictBlocked ? sendHold : null}
          failure={createFailure}
          onCreate={() => void createCv()}
        />
      )}

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
            holdSend={sendHold}
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
