const dateFormat = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium' })

export function formatDate(iso: string): string {
  return dateFormat.format(new Date(iso))
}

export function safeHttpUrl(url: string): string | null {
  try {
    const { protocol } = new URL(url)
    return protocol === 'http:' || protocol === 'https:' ? url : null
  } catch {
    return null
  }
}
