// Last server answer per screen, so a page you come back to opens filled in while the fresh copy loads.
// It lives in memory only and is emptied whenever the signed-in user changes.
const store = new Map<string, unknown>()

export const recall = <T>(key: string): T | undefined => store.get(key) as T | undefined
export const remember = (key: string, value: unknown) => void store.set(key, value)
export const forgetAll = () => store.clear()
