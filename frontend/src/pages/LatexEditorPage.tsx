import { useEffect, useState } from 'react'
import { api } from '../api/client'

const DEFAULT_SOURCE = `\\documentclass[11pt,a4paper]{article}
\\usepackage[margin=2cm]{geometry}
\\usepackage[T1]{fontenc}
\\usepackage[utf8]{inputenc}
\\usepackage{hyperref}

\\title{Curriculum Vitae}
\\author{Your Name}
\\date{}

\\begin{document}
\\maketitle

\\section*{Experience}
Backend engineer focused on Java, Spring Boot and distributed systems.

\\section*{Skills}
Java, Spring, PostgreSQL, Docker, LaTeX

\\end{document}
`

export function LatexEditorPage() {
  const [source, setSource] = useState(DEFAULT_SOURCE)
  const [pdfUrl, setPdfUrl] = useState<string | null>(null)
  const [compiling, setCompiling] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    return () => {
      if (pdfUrl) URL.revokeObjectURL(pdfUrl)
    }
  }, [pdfUrl])

  async function compile() {
    setCompiling(true)
    setError(null)
    try {
      const blob = await api.compileLatex(source)
      const nextUrl = URL.createObjectURL(blob)
      setPdfUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev)
        return nextUrl
      })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Compile failed')
    } finally {
      setCompiling(false)
    }
  }

  function downloadPdf() {
    if (!pdfUrl) return
    const link = document.createElement('a')
    link.href = pdfUrl
    link.download = 'document.pdf'
    link.click()
  }

  return (
    <section className="page latex-page">
      <div className="page-header">
        <div>
          <p className="eyebrow">CV lab</p>
          <h1>LaTeX editor</h1>
        </div>
        <div className="header-actions">
          <button type="button" className="button" onClick={compile} disabled={compiling}>
            {compiling ? 'Compiling…' : 'Compile'}
          </button>
          <button
            type="button"
            className="button button--ghost"
            onClick={downloadPdf}
            disabled={!pdfUrl}
          >
            Download PDF
          </button>
        </div>
      </div>

      {error && <pre className="banner banner--error latex-error">{error}</pre>}

      <div className="latex-split">
        <div className="latex-pane">
          <div className="latex-pane__label">Source</div>
          <textarea
            className="latex-editor"
            value={source}
            onChange={(e) => setSource(e.target.value)}
            spellCheck={false}
            aria-label="LaTeX source"
          />
        </div>
        <div className="latex-pane">
          <div className="latex-pane__label">PDF preview</div>
          {pdfUrl ? (
            <iframe title="PDF preview" className="latex-preview" src={pdfUrl} />
          ) : (
            <div className="latex-preview latex-preview--empty">
              <p>Compile the document to see the PDF here.</p>
            </div>
          )}
        </div>
      </div>
    </section>
  )
}
