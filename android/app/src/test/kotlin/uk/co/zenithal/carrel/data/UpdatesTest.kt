package uk.co.zenithal.carrel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdatesTest {
    private fun release(code: Int, notes: String = "", apk: Boolean = true, draft: Boolean = false, prerelease: Boolean = false) = GitHubRelease(
        tag = "android-v$code",
        name = "Carrel 0.$code.0",
        body = notes,
        draft = draft,
        prerelease = prerelease,
        assets = if (apk) listOf(GitHubAsset("carrel.apk", "https://example.com/v$code/carrel.apk", 5_000_000)) else emptyList(),
    )

    @Test
    fun readsTheVersionFromTheTag() {
        assertEquals(12, versionOf("android-v12"))
        assertNull(versionOf("v12"))
        assertNull(versionOf("android-vlatest"))
    }

    @Test
    fun listsTheChangesWithoutPullRequestNumbers() {
        val notes = "- Add an eReader (#33)\r\n* Keep downloaded books updating (#36)\n\nSome other text\n- \n"
        assertEquals(listOf("Add an eReader", "Keep downloaded books updating"), changesIn(notes))
        assertEquals(emptyList<String>(), changesIn(null))
    }

    @Test
    fun offersTheNewestReleaseWithEveryChangeSinceTheInstalledOne() {
        val releases = listOf(release(4, "- Four (#4)"), release(2, "- Two (#2)"), release(3, "- Three (#3)"), release(1, "- One (#1)"))
        val update = newestUpdate(releases, installed = 2)!!
        assertEquals(4, update.versionCode)
        assertEquals("0.4.0", update.versionName)
        assertEquals(listOf("Four", "Three"), update.changes)
        assertEquals("https://example.com/v4/carrel.apk", update.apkUrl)
    }

    @Test
    fun offersNothingWhenUpToDate() {
        assertNull(newestUpdate(listOf(release(2), release(1)), installed = 2))
        assertNull(newestUpdate(emptyList(), installed = 1))
    }

    @Test
    fun ignoresDraftsPrereleasesAndOtherTags() {
        val releases = listOf(release(5, draft = true), release(4, prerelease = true), release(3).copy(tag = "web-v3"), release(2))
        assertEquals(2, newestUpdate(releases, installed = 1)?.versionCode)
    }

    @Test
    fun needsTheApk() {
        assertNull(newestUpdate(listOf(release(2, apk = false)), installed = 1))
    }
}
