@file:OptIn(ExperimentalReadiumApi::class)

package uk.co.zenithal.carrel.reader

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.indexOfFirstWithHref

/** An entry in the book's contents: its title, the file it's in (its place in the reading order), and the element it starts at (null for the top). */
data class ContentsEntry(val title: String, val file: Int, val id: String?)

/**
 * The file on screen, laid out: how many screens Readium makes it (it's measured again when that changes), the first
 * column each of its contents entries is at the top of (by index in the entries), and how many columns hold its text. A
 * column is a page: one to a screen, or two side by side.
 */
data class FileLayout(val file: Int, val screens: Int, val starts: Map<Int, Int>, val columns: Int)

/** The chapter on the left (or only) page, and which of its pages are showing (`first` to `last`, of `pages`). */
data class ChapterPlace(val title: String, val first: Int, val last: Int, val pages: Int)

/** The book's contents entries, in contents order, leaving out any without a title or outside the book. */
fun contentsEntries(publication: Publication): List<ContentsEntry> =
    flatten(publication.tableOfContents, 0).mapNotNull { (link, _) ->
        val title = link.title?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val url = link.url()
        val file = publication.readingOrder.indexOfFirstWithHref(url) ?: return@mapNotNull null
        ContentsEntry(title, file, url.fragment)
    }

/**
 * The chapter at `column` of the file on screen: the last contents entry to start at or before it. Where two start in
 * the same place ("Part One" and its first chapter), the later in the contents wins. A chapter carried on from an earlier
 * file is counted from the top of this one. Null before the first entry, e.g. a cover not in the contents.
 */
fun chapterPlace(entries: List<ContentsEntry>, layout: FileLayout, column: Int, perScreen: Int): ChapterPlace? {
    // An entry whose element isn't found is taken as the top of its file.
    fun start(index: Int) = if (entries[index].file == layout.file) layout.starts[index] ?: 0 else 0
    val current = entries.indices
        .filter { entries[it].file < layout.file || (entries[it].file == layout.file && start(it) <= column) }
        .maxWithOrNull(compareBy({ entries[it].file }, { start(it) }, { it }))
        ?: return null
    val chapterStart = start(current)
    val end = entries.indices.filter { entries[it].file == layout.file }.map(::start).filter { it > chapterStart }.minOrNull()
        ?: maxOf(layout.columns, column + 1)
    val first = column - chapterStart + 1
    return ChapterPlace(entries[current].title, first, maxOf(first, minOf(column + perScreen, end) - chapterStart), end - chapterStart)
}

/** The footer's words, e.g. "(Page 3 / 9)", or "(Pages 5–6 / 20)" with two pages showing; the chapter's title goes before. */
fun pagesText(place: ChapterPlace) =
    if (place.first == place.last) "(Page ${place.first} / ${place.pages})" else "(Pages ${place.first}–${place.last} / ${place.pages})"

/**
 * Lays out the file on screen: where its contents entries start, and how many columns its text fills. If the page can't
 * be measured, every entry is taken as the top of the file, and every column as text.
 */
suspend fun measureFile(navigator: EpubNavigatorFragment, entries: List<ContentsEntry>, file: Int, screens: Int, perScreen: Int): FileLayout {
    val ids = entries.withIndex().filter { it.value.file == file && it.value.id != null }
    val script = """
        (function() {
            // Not innerWidth, which is rounded: a fraction of a pixel out adds up over a long file.
            var width = document.scrollingElement.scrollWidth / $screens / $perScreen;
            var column = function(rect) { return Math.floor((rect.left + window.scrollX + 1) / width); };
            // Where words or a picture show, or null for anything else. A picture can be cut, leaving a sliver at the
            // foot of one column, so it's where most of it is.
            var shown = function(node, last) {
                var range = document.createRange();
                var picture = /^(img|svg|image|video)$/i.test(node.nodeName);
                if (node.nodeType === Node.TEXT_NODE && node.textContent.trim()) range.selectNodeContents(node);
                else if (picture) range.selectNode(node);
                else return null;
                var rects = Array.prototype.filter.call(range.getClientRects(), function(r) { return r.width > 0 && r.height > 0; });
                if (picture) rects.sort(function(a, b) { return b.height - a.height; });
                return rects.length ? rects[picture || !last ? 0 : rects.length - 1] : null;
            };
            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
            // Where the first words or picture from the element on are: a heading's box can start at the foot of one
            // column while what's in it moves to the next.
            var start = function(element) {
                for (var node = walker.currentNode = element; node; node = walker.nextNode()) {
                    var rect = shown(node, false);
                    if (rect) return rect;
                }
                return element.getBoundingClientRect();
            };
            // Where the last words or picture before the element are, if any.
            var before = function(element) {
                walker.currentNode = element;
                for (var node = walker.previousNode(); node && node !== document.body; node = walker.previousNode()) {
                    var rect = shown(node, true);
                    if (rect) return rect;
                }
                return null;
            };
            // The text ends where the last words or picture are, not at the end of the screen, nor in the blank
            // column Readium adds to end two pages side by side evenly.
            var columns = $screens * $perScreen;
            walker.currentNode = document.body;
            while (walker.lastChild()) {}
            for (var node = walker.currentNode; node && node !== document.body; node = walker.previousNode()) {
                var rect = shown(node, true);
                if (rect) {
                    columns = column(rect) + 1;
                    break;
                }
            }
            var starts = ${JSONArray(ids.map { it.value.id })}.map(function(id) {
                var element = document.getElementById(id);
                if (!element) return -1;
                var first = column(start(element));
                var previous = before(element);
                // A page belongs to the chapter at its top, so one starting partway down a page starts on the next.
                return previous && column(previous) === first ? first + 1 : first;
            });
            return { columns: columns, starts: starts };
        })();
    """.trimIndent()
    val measured = try {
        navigator.evaluateJavascript(script)?.let(::JSONObject)
    } catch (_: JSONException) {
        null
    }
    val starts = measured?.optJSONArray("starts")
    return FileLayout(
        file,
        screens,
        ids.withIndex().mapNotNull { (i, entry) -> starts?.optInt(i, -1)?.takeIf { it >= 0 }?.let { entry.index to it } }.toMap(),
        measured?.optInt("columns")?.takeIf { it > 0 } ?: (screens * perScreen),
    )
}
