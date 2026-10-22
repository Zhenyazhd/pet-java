import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { ErrorBoundary } from './components/ErrorBoundary'
// Fonts come from this server, not a CDN: no third party sees visitors, and the strict CSP stays 'self'.
// The files without a subset in the name declare a unicode-range per script (latin, latin-ext, cyrillic, ...),
// so the browser fetches only the scripts a page uses. Importing single subsets instead leaves two faces with the
// same range, and the later one wins for every character.
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/500.css'
import './styles/tokens.css'
import './styles/base.css'
import './styles/ui.css'
import './styles/shell.css'
import './styles/auth.css'
import './styles/profile.css'
import './styles/dialog.css'
import './styles/vacancies.css'
import './styles/resume.css'
import './styles/jobs.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </StrictMode>,
)
