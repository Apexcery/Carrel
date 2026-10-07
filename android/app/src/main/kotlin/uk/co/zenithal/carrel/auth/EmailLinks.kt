package uk.co.zenithal.carrel.auth

import android.net.Uri
import io.github.jan.supabase.auth.OtpType

/**
 * A link from one of Carrel's emails (/auth/confirm?token_hash=…&type=…), which the website also handles. The link
 * types are the ones Carrel's email templates use (configured in Supabase; see the README). A link missing either
 * part has null for it, and can't be used.
 */
data class EmailLink(val tokenHash: String?, val type: OtpType.Email?) {
    /** Signing in by the link means choosing a password next: resetting one, or setting one when invited. */
    val setsPassword get() = type == OtpType.Email.RECOVERY || type == OtpType.Email.INVITE

    companion object {
        private val TYPES = listOf(OtpType.Email.EMAIL, OtpType.Email.INVITE, OtpType.Email.RECOVERY, OtpType.Email.EMAIL_CHANGE)

        /** The link, if the address is one; any other address is null. */
        fun from(uri: Uri?): EmailLink? {
            if (uri?.path != "/auth/confirm") return null
            val type = uri.getQueryParameter("type")
            return EmailLink(uri.getQueryParameter("token_hash"), TYPES.firstOrNull { it.type == type })
        }
    }
}
