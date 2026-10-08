import { useEffect } from 'react'

const APP_NAME = 'Job Search'

export function useDocumentTitle(title: string) {
  useEffect(() => {
    document.title = `${title} · ${APP_NAME}`
  }, [title])
}
