import { createPortal } from 'react-dom'
import { Button } from '../ui/Button'

type PdfPreviewModalProps = {
  url: string | null
  onClose: () => void
}

export function PdfPreviewModal({ url, onClose }: PdfPreviewModalProps) {
  if (!url) return null

  return createPortal(
    <div className="pdf-modal" role="dialog" aria-label="PDF preview">
      <div className="pdf-modal__bar">
        <strong>PDF preview</strong>
        <Button variant="ghost" onClick={onClose}>
          Close
        </Button>
      </div>
      <iframe title="Resume PDF" src={url} className="pdf-modal__frame" />
    </div>,
    document.body,
  )
}
