package uk.co.zenithal.carrel.auth

/** Supabase Auth is set to require at least this many characters; checked here too so the message is clear. */
const val MIN_PASSWORD_LENGTH = 8

/** Supabase hashes passwords with bcrypt, which only uses the first 72 bytes, so it rejects longer ones. */
private const val MAX_PASSWORD_BYTES = 72

/** Why a new password won't be accepted, or null if it's fine. */
fun passwordProblem(password: String): String? = when {
    password.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters."
    password.toByteArray(Charsets.UTF_8).size > MAX_PASSWORD_BYTES -> "Use 72 characters or fewer."
    else -> null
}
