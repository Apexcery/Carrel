package uk.co.zenithal.carrel.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ImportSource(val label: String) {
    @SerialName("goodreads") Goodreads("Goodreads"),
    @SerialName("story_graph") StoryGraph("StoryGraph"),
    @SerialName("carrel") Carrel("Carrel"),
}

/** Matching rows to books, waiting for Hardcover's daily allowance, finished, or given up after repeated failures. */
@Serializable
enum class ImportState {
    @SerialName("matching") Matching,
    @SerialName("waiting") Waiting,
    @SerialName("done") Done,
    @SerialName("failed") Failed,
}

/** An import's progress (GET /imports/latest). */
@Serializable
data class ImportStatus(
    val id: Long,
    val source: ImportSource,
    val state: ImportState,
    val total: Int,
    val matched: Int,
    /** Matched by title and not checked yet. */
    val toCheck: Int,
    val notFound: Int,
    /** Still being matched. */
    val remaining: Int,
) {
    val active get() = state == ImportState.Matching || state == ImportState.Waiting

    /** Finished, with every book it flagged checked, chosen, or skipped. */
    val settled get() = state == ImportState.Done && toCheck == 0 && notFound == 0
}

@Serializable
enum class ImportMatch {
    @SerialName("by_title") ByTitle,
    @SerialName("not_found") NotFound,
}

/** A row of an import to sort out: matched by title only, or not found. */
@Serializable
data class ImportReviewItem(
    val id: Long,
    /** As the export has it. */
    val title: String,
    val authors: List<String>,
    val match: ImportMatch,
    /** The book a title match found; null when nothing matched. */
    val book: ImportedBook?,
)

@Serializable
data class ImportedBook(
    val id: Long,
    val title: String,
    val authors: List<String>,
    val coverUrl: String?,
    val firstPublishedYear: Int?,
    val seriesName: String?,
    val seriesPosition: Double?,
)

@Serializable
data class ChooseBookRequest(val hardcoverId: Int?, val openLibraryId: String?)

/** A title from an export, with any series Goodreads put on the end of it split off. */
data class ExportedTitle(val title: String, val series: String?, val position: String?)

private val SERIES_SUFFIX = Regex("""^(.+?)\s*\(([^()]+?),?\s*#(\d+(?:\.\d+)?)\)\s*$""")

/**
 * Goodreads titles end with the series, as in "Going to Ground (The Shapeshifter, #3)"; this splits it off. Titles
 * without one, including every StoryGraph title, come back whole.
 */
fun splitSeries(title: String): ExportedTitle =
    SERIES_SUFFIX.find(title)?.destructured?.let { (name, series, position) -> ExportedTitle(name, series, position) }
        ?: ExportedTitle(title, null, null)

/** What to search for to find an exported book: its title without anything in brackets, and its first author. */
fun pickerQuery(title: String, authors: List<String>): String =
    "${title.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "")} ${authors.firstOrNull().orEmpty()}".trim()
