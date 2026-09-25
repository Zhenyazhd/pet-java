/** Immutable list helpers for resume sheet edits. */

export function updateAt<T extends object>(
  list: readonly T[],
  index: number,
  patch: Partial<T>,
): T[] {
  return list.map((item, i) => (i === index ? { ...item, ...patch } : item))
}

export function setAt<T>(list: readonly T[], index: number, value: T): T[] {
  return list.map((item, i) => (i === index ? value : item))
}

export function removeAt<T>(list: readonly T[], index: number): T[] {
  return list.filter((_, i) => i !== index)
}
