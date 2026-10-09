package uk.co.zenithal.carrel

import android.app.Application
import android.content.Context
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import uk.co.zenithal.carrel.auth.SessionWatcher
import uk.co.zenithal.carrel.data.ApiClient
import uk.co.zenithal.carrel.data.AppUpdates
import uk.co.zenithal.carrel.auth.Session
import uk.co.zenithal.carrel.data.CarrelDatabase
import uk.co.zenithal.carrel.data.ChangesDatabase
import uk.co.zenithal.carrel.data.Connectivity
import uk.co.zenithal.carrel.data.CarrelJson
import uk.co.zenithal.carrel.data.GoalChanges
import uk.co.zenithal.carrel.data.LibraryChanges
import uk.co.zenithal.carrel.data.OnlineLibraries
import uk.co.zenithal.carrel.data.Outbox
import uk.co.zenithal.carrel.data.Secrets
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import uk.co.zenithal.carrel.data.PhoneBooks
import uk.co.zenithal.carrel.data.PhoneBooksDatabase
import uk.co.zenithal.carrel.data.RecentSearches
import uk.co.zenithal.carrel.data.Store
import uk.co.zenithal.carrel.reader.OpenBook
import uk.co.zenithal.carrel.ui.theme.Appearance

class CarrelApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, MainScope())
    }
}

/** The app's shared services, created once. Each build type points them at its own Supabase project and API. */
class AppContainer(context: Context, val scope: CoroutineScope) {
    val supabase = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
        install(Auth) {
            // Email links carry a token hash that works on any device. (PKCE would tie a sign-up's confirmation link to
            // the device that started it, so it couldn't be opened on a laptop.)
            flowType = FlowType.IMPLICIT
        }
    }
    val api = ApiClient(BuildConfig.API_URL, supabase, CarrelJson)
    val store = Store(CarrelDatabase.create(context).responses(), api, scope)
    val connectivity = Connectivity(context)
    val outbox = Outbox(context, ChangesDatabase.create(context).changes()) { (session.state.value as? Session.SignedIn)?.userId }
    val library = LibraryChanges(api, store, outbox)
    val goals = GoalChanges(api, store)
    private val phoneBooksDatabase = PhoneBooksDatabase.create(context)
    val phoneBooks = PhoneBooks(context, phoneBooksDatabase.books(), context.getSharedPreferences("reading", Context.MODE_PRIVATE))
    val libraries = OnlineLibraries(phoneBooksDatabase.libraries(), Secrets())
    /** Covers from online libraries, which may need the library's login. */
    val libraryImages = ImageLoader.Builder(context).logger(coil3.util.DebugLogger()).components { add(OkHttpNetworkFetcherFactory(callFactory = { libraries.http })) }.build()
    /** The book open in the reader (ReaderActivity), if any. */
    var openBook: OpenBook? = null
    val recentSearches = RecentSearches(context.getSharedPreferences("recent-searches", Context.MODE_PRIVATE))
    val appearance = Appearance(context.getSharedPreferences("appearance", Context.MODE_PRIVATE))
    val session: SessionWatcher = SessionWatcher(supabase, store, outbox, context.getSharedPreferences("saved-data", Context.MODE_PRIVATE), scope)
    val shortcuts = LauncherShortcuts(context, store, scope)
    val updates = AppUpdates(context, scope)

    init {
        scope.launch { outbox.scheduleIfWaiting() }
        scope.launch { libraries.load() }
    }
}
