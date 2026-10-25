export const MIN_PASSWORD_LENGTH = 8
/** bcrypt reads at most 72 bytes of a password, and the server refuses longer ones. */
const MAX_PASSWORD_BYTES = 72

/** What is wrong with a new password, or null; the server checks the same limits. */
export function passwordProblem(password: string): string | null {
  if (password.length < MIN_PASSWORD_LENGTH) return `Password must be at least ${MIN_PASSWORD_LENGTH} characters.`
  if (new TextEncoder().encode(password).length > MAX_PASSWORD_BYTES) {
    return 'Password is too long — use at most 72 bytes (about 72 ASCII characters).'
  }
  return null
}
