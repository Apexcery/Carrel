package uk.co.zenithal.carrel

import android.app.Application
import android.content.Context
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import uk.co.zenithal.carrel.auth.SessionWatcher
import uk.co.zenithal.carrel.data.ApiClient
import uk.co.zenithal.carrel.data.CarrelDatabase
import uk.co.zenithal.carrel.data.CarrelJson
import uk.co.zenithal.carrel.data.Store

class CarrelApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, MainScope())
    }
}

/** The app's shared services, created once. Each build type points them at its own Supabase project and API. */
class AppContainer(context: Context, scope: CoroutineScope) {
    val supabase = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
        install(Auth) {
            // Email links carry a token hash that works on any device. (PKCE would tie a sign-up's confirmation link to
            // the device that started it, so it couldn't be opened on a laptop.)
            flowType = FlowType.IMPLICIT
        }
    }
    val api = ApiClient(BuildConfig.API_URL, supabase, CarrelJson)
    val store = Store(CarrelDatabase.create(context).responses(), api)
    val session = SessionWatcher(supabase, store, context.getSharedPreferences("saved-data", Context.MODE_PRIVATE), scope)
}
