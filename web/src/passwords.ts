/** Supabase Auth is set to require at least this many characters; checked here too so the message is clear. */
export const MIN_PASSWORD_LENGTH = 8

/** Supabase hashes passwords with bcrypt, which only uses the first 72 bytes, so it rejects longer ones. */
const MAX_PASSWORD_BYTES = 72

/** Why a new password won't be accepted, or null if it's fine. */
export function passwordProblem(password: string): string | null {
  if (password.length < MIN_PASSWORD_LENGTH) {
    return `Use at least ${MIN_PASSWORD_LENGTH} characters.`
  }
  if (new TextEncoder().encode(password).length > MAX_PASSWORD_BYTES) {
    return 'Use 72 characters or fewer.'
  }
  return null
}
