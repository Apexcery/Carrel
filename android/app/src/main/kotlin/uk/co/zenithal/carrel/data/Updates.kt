package uk.co.zenithal.carrel.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import uk.co.zenithal.carrel.BuildConfig
import uk.co.zenithal.carrel.CarrelApp
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A release on GitHub, as its API lists them. The app's are tagged android-v<versionCode>, with the APK as carrel.apk. */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(val name: String, @SerialName("browser_download_url") val url: String, val size: Long = 0)

/** A newer version of the app: the latest release, with the changes in every release since the installed one. */
data class AppUpdate(val versionCode: Int, val versionName: String, val changes: List<String>, val apkUrl: String, val size: Long)

sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val fraction: Float) : InstallState
    /** Handed to Android, which may ask the reader to confirm, then closes Carrel to replace it and reopens it. */
    data object Installing : InstallState
    data class Failed(val message: String) : InstallState
}

/**
 * Checks GitHub for a newer release of the app, and installs it. Each launch checks once, offering an update unless
 * the reader skipped that version (or a later one); Settings can check at any time, skipped or not.
 */
class AppUpdates(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    private val _offered = MutableStateFlow<AppUpdate?>(null)
    /** The update the update screen shows, until the reader chooses. */
    val offered: StateFlow<AppUpdate?> = _offered

    private val _install = MutableStateFlow<InstallState>(InstallState.Idle)
    val install: StateFlow<InstallState> = _install

    private var launchChecked = false

    /**
     * Offers a newer release than the installed one, unless it's been skipped. Once per process, when the app first
     * opens (not when it's started in the background, e.g. to sync). Quietly does nothing without a signal.
     */
    fun checkOnLaunch() {
        if (launchChecked) return
        launchChecked = true
        scope.launch {
            val update = try {
                latest()
            } catch (_: IOException) {
                null
            }
            if (update != null && update.versionCode > prefs.getInt(SKIPPED, 0)) _offered.value = update
        }
    }

    /** Settings' check: offers any newer release, even a skipped one. False when this is the latest. */
    suspend fun check(): Boolean {
        val update = latest() ?: return false
        _offered.value = update
        return true
    }

    /** Hides the update until the next launch. */
    fun notNow() {
        _offered.value = null
        _install.value = InstallState.Idle
    }

    /** Hides the update until a later one is released. */
    fun skip(update: AppUpdate) {
        prefs.edit().putInt(SKIPPED, update.versionCode).apply()
        notNow()
    }

    /** Downloads the APK straight into an install session, then hands it to Android. */
    fun install(update: AppUpdate) {
        if (_install.value is InstallState.Downloading || _install.value == InstallState.Installing) return
        _install.value = InstallState.Downloading(0f)
        scope.launch {
            _install.value = try {
                withContext(Dispatchers.IO) { downloadAndCommit(update) }
                InstallState.Installing
            } catch (_: IOException) {
                InstallState.Failed("Couldn’t download the update. Check your connection and try again.")
            } catch (_: Exception) {
                InstallState.Failed(DIDNT_INSTALL)
            }
        }
    }

    internal fun cancelled() {
        _install.value = InstallState.Idle
    }

    internal fun failed() {
        _install.value = InstallState.Failed(DIDNT_INSTALL)
    }

    private suspend fun latest(): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(BuildConfig.RELEASES_URL).header("Accept", "application/vnd.github+json").build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val releases = try {
                CarrelJson.decodeFromString(ListSerializer(GitHubRelease.serializer()), r.body.string())
            } catch (e: IllegalArgumentException) {
                throw IOException(e)
            }
            newestUpdate(releases, BuildConfig.VERSION_CODE)
        }
    }

    private fun downloadAndCommit(update: AppUpdate) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (update.size > 0) setSize(update.size)
            // Once Carrel installed the version it's replacing, Android doesn't ask the reader to confirm. The first
            // time (installed from a browser or adb) it still does.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                http.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    val total = r.body.contentLength().takeIf { it > 0 } ?: update.size
                    session.openWrite("carrel.apk", 0, if (total > 0) total else -1).use { output ->
                        r.body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                if (total > 0) _install.value = InstallState.Downloading(done.toFloat() / total)
                            }
                        }
                        session.fsync(output)
                    }
                }
                val status = PendingIntent.getBroadcast(
                    context, id, Intent(context, InstallStatusReceiver::class.java),
                    // Mutable, so Android can add the status.
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                session.commit(status.intentSender)
            }
        } catch (e: Exception) {
            installer.abandonSession(id)
            throw e
        }
    }

    private companion object {
        const val SKIPPED = "skipped"
        const val DIDNT_INSTALL = "The update didn’t install. Try again later."
    }
}

/** What Android says about an install: asking the reader to confirm, a cancel, or a failure. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updates = (context.applicationContext as CarrelApp).container.updates
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            // Android closes Carrel to replace it, then reopens it, so there's nothing to do.
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> updates.cancelled()
            else -> updates.failed()
        }
    }
}

/** The newest release above `installed` with an APK, and the changes in it and every release between. */
fun newestUpdate(releases: List<GitHubRelease>, installed: Int): AppUpdate? {
    val newer = releases
        .filter { !it.draft && !it.prerelease }
        .mapNotNull { release -> versionOf(release.tag)?.let { it to release } }
        .filter { (code, _) -> code > installed }
        .sortedByDescending { (code, _) -> code }
    val (code, latest) = newer.firstOrNull() ?: return null
    val apk = latest.assets.firstOrNull { it.name == "carrel.apk" } ?: return null
    return AppUpdate(
        versionCode = code,
        versionName = latest.name?.removePrefix("Carrel ") ?: code.toString(),
        changes = newer.flatMap { (_, release) -> changesIn(release.body) },
        apkUrl = apk.url,
        size = apk.size,
    )
}

/** The versionCode in a release's tag (android-v12), or null for any other tag. */
fun versionOf(tag: String): Int? = if (tag.startsWith(TAG_PREFIX)) tag.removePrefix(TAG_PREFIX).toIntOrNull() else null

/** A release's notes as a list of changes: its bullet points, without their pull request numbers. */
fun changesIn(notes: String?): List<String> =
    notes.orEmpty().lines()
        .map { it.trim() }
        .filter { it.startsWith("- ") || it.startsWith("* ") }
        .map { it.drop(2).replace(PULL_REQUEST, "").trim() }
        .filter { it.isNotEmpty() }

private const val TAG_PREFIX = "android-v"
private val PULL_REQUEST = Regex("""\s*\(#\d+\)$""")
