package uk.co.zenithal.carrel.auth

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.auth.exception.AuthRestException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import uk.co.zenithal.carrel.data.ApiClient
import uk.co.zenithal.carrel.data.Store

sealed interface Session {
    /** The saved session is still being read. */
    data object Loading : Session
    data object SignedOut : Session
    data class SignedIn(val userId: String, val email: String?) : Session
}

/**
 * The reader's session, kept from Supabase Auth. Saved API data is cleared whenever the reader changes, including
 * signing out. Whose data it is is kept on the device, so opening the app again doesn't count as a change.
 */
class SessionWatcher(supabase: SupabaseClient, store: Store, private val prefs: SharedPreferences, scope: CoroutineScope) {
    val state: StateFlow<Session> = supabase.auth.sessionStatus
        .map { status ->
            when (status) {
                SessionStatus.Initializing -> Session.Loading
                is SessionStatus.Authenticated -> status.session.user
                    ?.let { Session.SignedIn(it.id, it.email) } ?: Session.SignedOut
                // Refreshing failed, usually for want of a signal: the reader is still signed in on this device.
                is SessionStatus.RefreshFailure -> supabase.auth.currentUserOrNull()
                    ?.let { Session.SignedIn(it.id, it.email) } ?: Session.SignedOut
                is SessionStatus.NotAuthenticated -> Session.SignedOut
            }
        }
        .distinctUntilChanged()
        .onEach { session ->
            if (session == Session.Loading) return@onEach
            // A different reader (or none) means none of the saved data is theirs. Signed out, the owner is "".
            val next = (session as? Session.SignedIn)?.userId.orEmpty()
            if (prefs.getString(SAVED_DATA_OWNER, null) != next) {
                store.clear()
                prefs.edit { putString(SAVED_DATA_OWNER, next) }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, Session.Loading)

    private companion object {
        const val SAVED_DATA_OWNER = "savedDataOwner"
    }
}

/** A readable message for a failed Supabase Auth call, as the website shows. */
fun authMessage(error: Throwable): String = when (error) {
    is AuthRestException -> error.errorDescription
    is RestException -> error.description ?: error.error
    is HttpRequestException -> ApiClient.CANT_CONNECT
    else -> "Something went wrong. Try again."
}
