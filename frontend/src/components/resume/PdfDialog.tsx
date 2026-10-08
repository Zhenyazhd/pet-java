import { Dialog } from '../Dialog'
import { Button } from '../ui/Button'

const TITLE_ID = 'pdf-dialog-title'

type PdfDialogProps = { url: string; onClose: () => void }

export function PdfDialog({ url, onClose }: PdfDialogProps) {
  return (
    <Dialog labelledBy={TITLE_ID} onClose={onClose}>
      <header className="dialog__header">
        <h2 id={TITLE_ID} className="dialog__title">
          PDF preview
        </h2>
        <Button variant="ghost" onClick={onClose}>
          Close
        </Button>
      </header>
      <iframe className="pdf-frame" title="Resume PDF" src={url} />
      <p className="pdf-links">
        <a href={url} target="_blank" rel="noreferrer noopener">
          Open in a new tab<span className="sr-only"> (opens in a new tab)</span>
        </a>
        <a href={url} download="cv.pdf">
          Download
        </a>
      </p>
    </Dialog>
  )
}
