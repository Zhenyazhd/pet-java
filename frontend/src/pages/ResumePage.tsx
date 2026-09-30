import { AiAssistPanel } from '../components/resume/AiAssistPanel'
import { CvSheet } from '../components/resume/CvSheet'
import { PdfPreviewModal } from '../components/resume/PdfPreviewModal'
import { VacancyContextModal } from '../components/resume/VacancyContextModal'
import { VacancyMatchPanel } from '../components/resume/VacancyMatchPanel'
import { Banner } from '../components/ui/Banner'
import { Button } from '../components/ui/Button'
import { PageHeader } from '../components/ui/PageHeader'
import { useResumeEditor } from '../lib/useResumeEditor'

export function ResumePage() {
  const editor = useResumeEditor()

  if (!editor.resume) {
    return (
      <section className="page">
        <p className="page-lead">{editor.error ?? 'Loading resume…'}</p>
      </section>
    )
  }

  const structuralBusy = editor.contextBusy
  const anyResumeOpBusy =
    structuralBusy || editor.saving || editor.previewing || editor.aiBusy || editor.matching

  return (
    <section className="page resume-page">
      <PageHeader
        eyebrow="Curriculum"
        title="Resume"
        lead="Edit the sheet by hand or chat with AI. Click a block to focus the chat; click empty sheet space for the full resume."
        actions={
          <>
            <div className="locale-switch" role="group" aria-label="Resume language">
              <button
                type="button"
                className={`locale-switch__btn ${editor.resume.locale === 'fr' ? 'locale-switch__btn--active' : ''}`}
                onClick={() => editor.setLocale('fr')}
              >
                FR
              </button>
              <button
                type="button"
                className={`locale-switch__btn ${editor.resume.locale === 'en' ? 'locale-switch__btn--active' : ''}`}
                onClick={() => editor.setLocale('en')}
              >
                EN
              </button>
            </div>
            <Button
              variant="ghost"
              active={Boolean(editor.vacancyContext.trim())}
              onClick={editor.openContextModal}
              disabled={structuralBusy}
            >
              {editor.vacancyContext.trim() ? 'Edit context' : 'Add context'}
            </Button>
            <Button variant="ghost" onClick={editor.previewPdf} disabled={editor.previewing || structuralBusy}>
              {editor.previewing ? 'Preparing PDF…' : 'Preview PDF'}
            </Button>
            <Button onClick={editor.save} disabled={editor.saving || structuralBusy}>
              {editor.saving ? 'Saving…' : 'Save'}
            </Button>
          </>
        }
      />

      {editor.vacancyContext.trim() && (
        <p className="context-pill">
          Vacancy context attached ({editor.vacancyContext.trim().length} chars) — used in AI chat
        </p>
      )}

      {editor.conflict && (
        <Banner tone="error">
          <p>{editor.error}</p>
          <Button variant="ghost" onClick={() => void editor.reloadResume()} disabled={anyResumeOpBusy}>
            Reload resume
          </Button>
        </Banner>
      )}
      {editor.error && !editor.conflict && <Banner tone="error">{editor.error}</Banner>}
      {editor.status && !editor.error && <Banner tone="ok">{editor.status}</Banner>}

      <div className="resume-layout resume-layout--assist">
        <CvSheet
          resume={editor.resume}
          selected={editor.selected}
          onClearFocus={editor.clearFocus}
          onSelect={editor.select}
          onPatch={editor.patch}
        />
        <AiAssistPanel
          resume={editor.resume}
          selected={editor.selected}
          chat={editor.chat}
          draft={editor.draft}
          busy={editor.aiBusy || structuralBusy}
          hasVacancyContext={Boolean(editor.vacancyContext.trim())}
          aiModel={editor.aiModel}
          onAiModelChange={editor.setAiModel}
          onClearFocus={editor.clearFocus}
          onDraftChange={editor.setDraft}
          onSend={editor.sendChat}
          onApply={editor.applySuggestion}
        />
      </div>

      <VacancyMatchPanel
        hasContext={Boolean(editor.vacancyContext.trim())}
        matching={editor.matching}
        busy={structuralBusy}
        report={editor.matchReport}
        error={editor.matchError}
        onCheck={() => void editor.checkVacancyMatch()}
        onDismissError={() => editor.setMatchError(null)}
      />

      <PdfPreviewModal url={editor.pdfUrl} onClose={editor.closePdf} />

      <VacancyContextModal
        open={editor.contextOpen}
        draft={editor.contextDraft}
        disabled={structuralBusy}
        saving={structuralBusy}
        onDraftChange={editor.setContextDraft}
        onClose={editor.closeContextModal}
        onClear={() => {
          void editor.commitVacancyContext('')
        }}
        onSave={() => {
          void editor.commitVacancyContext(editor.contextDraft)
        }}
      />
    </section>
  )
}
