async function readErrorMessage(response: Response): Promise<string> {
  const text = await response.text()
  let message = `Request failed (${response.status})`
  try {
    const data = JSON.parse(text)
    if (typeof data?.message === 'string') {
      message = data.message
    }
  } catch {
    if (text) message = text
  }
  return message
}

export const api = {
  compileLatex: async (source: string): Promise<Blob> => {
    const response = await fetch('/api/latex/compile', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ source }),
    })

    if (!response.ok) {
      throw new Error(await readErrorMessage(response))
    }

    return response.blob()
  },
}
