package uk.co.zenithal.carrel.ui.you

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch
import uk.co.zenithal.carrel.BuildConfig
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.data.ApiException
import uk.co.zenithal.carrel.data.LIBRARY_PATH
import uk.co.zenithal.carrel.data.LibraryItem
import uk.co.zenithal.carrel.data.LibrarySerializer
import uk.co.zenithal.carrel.data.MAX_GOAL_BOOKS
import uk.co.zenithal.carrel.data.Profile
import uk.co.zenithal.carrel.data.ReadingGoal
import uk.co.zenithal.carrel.data.formatCount
import uk.co.zenithal.carrel.data.goalPace
import uk.co.zenithal.carrel.data.readsFinishedIn
import uk.co.zenithal.carrel.data.yearInBooks
import uk.co.zenithal.carrel.ui.components.ErrorNotice
import uk.co.zenithal.carrel.ui.components.Field
import uk.co.zenithal.carrel.ui.components.FormMessage
import uk.co.zenithal.carrel.ui.components.ImageViewer
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.PrimaryButton
import uk.co.zenithal.carrel.ui.components.SecondaryButton
import uk.co.zenithal.carrel.ui.components.SectionTitle
import uk.co.zenithal.carrel.ui.components.SkeletonLine
import uk.co.zenithal.carrel.ui.components.Tone
import uk.co.zenithal.carrel.ui.library.ProgressBar
import uk.co.zenithal.carrel.ui.rememberLoaded
import uk.co.zenithal.carrel.ui.settings.SettingsButton
import uk.co.zenithal.carrel.ui.theme.Carrel
import java.time.LocalDateTime
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The signed-in reader's profile, as the website's, less the shelves (those are in the Library tab): their picture,
 * username, and book count, sharing it while it's public, this year's reading goal, and their year so far.
 */
@Composable
fun ProfileSection(profile: Profile, openSettings: () -> Unit) {
    val library = rememberLoaded(LIBRARY_PATH, LibrarySerializer)
    val items = library.loaded.data
    val year = LocalDateTime.now().year

    Header(profile, items?.size, openSettings)
    when {
        items != null -> {
            ReadingGoal(items, profile.goals, year)
            YearInBooks(items, year)
        }
        library.loaded.error != null -> ErrorNotice(library.loaded.error.message.orEmpty(), library.retry, Modifier.padding(top = 32.dp))
        else -> Column(Modifier.padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SkeletonLine(0.4f)
            SkeletonLine(1f)
            SkeletonLine(0.6f)
        }
    }
}

@Composable
private fun Header(profile: Profile, books: Int?, openSettings: () -> Unit) {
    val colors = Carrel.colors
    val context = LocalContext.current
    val username = profile.username.orEmpty()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Avatar(profile.avatarUrl, username)
        Column(Modifier.weight(1f)) {
            Text("@$username", style = Carrel.type.displayMedium, color = colors.ink)
            val details = listOfNotNull(books?.let { "${formatCount(it)} ${if (it == 1) "book" else "books"}" }, if (profile.isPublic) "Public" else "Private")
            Text(details.joinToString(" · "), style = Carrel.type.mono, color = colors.inkSoft, modifier = Modifier.padding(top = 6.dp))
        }
        // Its touch area reaches into the margins, so the gear itself lines up with the page's edge.
        SettingsButton(openSettings, Modifier.align(Alignment.Top).offset(x = 12.dp, y = (-12).dp))
    }
    // A private profile's address shows others nothing, so there's nothing to share.
    if (profile.isPublic) {
        LinkButton("Share your profile", {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "${BuildConfig.WEBSITE_URL}/@$username")
            context.startActivity(Intent.createChooser(send, "Share your profile"))
        }, Modifier.padding(top = 14.dp), color = colors.ink)
    }
}

/** The reader's round picture, which opens larger when tapped, or a silhouette without one. */
@Composable
fun Avatar(url: String?, username: String) {
    val colors = Carrel.colors
    var zoomed by remember { mutableStateOf(false) }
    val frame = Modifier.size(72.dp).clip(CircleShape).background(colors.paperRaised)
    if (url == null) {
        Canvas(frame.border(1.dp, colors.rule, CircleShape)) {
            // The website's silhouette (a 24-unit square), a little lower so the shoulders meet the edge.
            val unit = size.width / 24
            val drop = size.height * 0.06f
            drawCircle(colors.inkFaint, radius = 4.5f * unit, center = Offset(12 * unit, 9 * unit + drop))
            drawPath(
                Path().apply {
                    moveTo(3.5f * unit, 24 * unit + drop)
                    cubicTo(3.5f * unit, 19 * unit + drop, 7.3f * unit, 15.5f * unit + drop, 12 * unit, 15.5f * unit + drop)
                    cubicTo(16.7f * unit, 15.5f * unit + drop, 20.5f * unit, 19 * unit + drop, 20.5f * unit, 24 * unit + drop)
                    close()
                },
                colors.inkFaint,
            )
        }
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = "@$username’s profile picture",
        contentScale = ContentScale.Crop,
        modifier = frame.clickable(onClickLabel = "View the picture larger", role = Role.Image) { zoomed = true },
    )
    if (zoomed) ImageViewer(url, "@$username’s profile picture") { zoomed = false }
}

/**
 * This year's reading goal: books finished (rereads count again) against the goal, and whether that's ahead of or
 * behind the pace the goal needs. The reader's own is set, changed, and removed here; another reader's (`owner`) only
 * shows, and not at all when they haven't set one.
 */
@Composable
internal fun ReadingGoal(items: List<LibraryItem>, goals: List<ReadingGoal>, year: Int, owner: String? = null) {
    val colors = Carrel.colors
    var editing by rememberSaveable { mutableStateOf(false) }
    val goal = goals.firstOrNull { it.year == year }
    val finished = remember(items, year) { readsFinishedIn(items, year).size }
    if (goal == null && owner != null) return

    Column(Modifier.padding(top = 48.dp)) {
        SectionTitle(if (owner == null) "$year reading goal" else "@$owner’s $year goal")
        Column(Modifier.padding(top = 14.dp)) {
            when {
                editing -> GoalForm(year, goal?.books) { editing = false }
                goal != null -> {
                    Text(
                        buildAnnotatedString {
                            withStyle(Carrel.type.heading.toSpanStyle().copy(color = colors.ink)) { append(formatCount(finished)) }
                            append(" of ${formatCount(goal.books)} ${if (goal.books == 1) "book" else "books"}")
                        },
                        style = Carrel.type.body,
                        color = colors.inkSoft,
                    )
                    ProgressBar(minOf(100.0, finished * 100.0 / goal.books), Modifier.padding(top = 10.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(goalPace(finished, goal.books, LocalDateTime.now()), style = Carrel.type.mono, color = colors.inkSoft, modifier = Modifier.weight(1f))
                        if (owner == null) LinkButton("Change", { editing = true })
                    }
                }
                else -> SecondaryButton("Set a reading goal", { editing = true })
            }
        }
    }
}

/** Sets this year's goal, or removes it; the saved goals go into the profile, so the panel shows them straight away. */
@Composable
private fun GoalForm(year: Int, current: Int?, onDone: () -> Unit) {
    val goals = LocalContainer.current.goals
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf(current?.toString() ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val value = text.trim().toIntOrNull()
    val valid = value != null && value in 1..MAX_GOAL_BOOKS

    fun run(action: suspend () -> Unit) = scope.launch {
        busy = true
        error = null
        try {
            action()
            onDone()
        } catch (e: ApiException) {
            error = e.message
        } finally {
            busy = false
        }
    }
    val save = { if (valid && !busy) run { goals.save(year, value!!) } }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Field(
            "Books to finish in $year",
            text,
            { text = it.filter(Char::isDigit) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            onDone = { save() },
        )
        if (text.isNotBlank() && !valid) FormMessage("Choose a goal from 1 to ${formatCount(MAX_GOAL_BOOKS)} books.", Tone.Error)
        error?.let { FormMessage(it, Tone.Error) }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(if (busy) "Saving…" else "Save", { save() }, enabled = valid && !busy)
            LinkButton("Cancel", onDone)
            if (current != null) LinkButton("Remove", { if (!busy) run { goals.remove(year) } })
        }
    }
}

/**
 * A reader's year so far: books finished (rereads count again), pages read, average rating, and books finished each
 * month. Tapping a month lists its books, as hovering does on the website. `owner` is another reader's username.
 */
@Composable
internal fun YearInBooks(items: List<LibraryItem>, year: Int, owner: String? = null) {
    val colors = Carrel.colors
    val stats = remember(items, year) { yearInBooks(items, year) }
    var month by rememberSaveable { mutableStateOf<Int?>(null) }

    Column(Modifier.padding(top = 48.dp)) {
        SectionTitle(if (owner == null) "Your $year" else "@$owner’s $year")
        if (stats.finished == 0) {
            Text(
                if (owner == null) "Nothing finished yet this year. Books you mark as read will add up here." else "Nothing finished yet this year.",
                style = Carrel.type.body,
                color = colors.inkSoft,
                modifier = Modifier.padding(top = 14.dp),
            )
            return
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Books finished", formatCount(stats.finished), Modifier.weight(1f))
            Stat("Pages read", formatCount(stats.pages), Modifier.weight(1f))
            // Left out until at least one of this year's books has a rating.
            stats.averageRating?.let { Stat("Average rating", "%.1f".format(it), Modifier.weight(1f)) }
        }
        val most = maxOf(stats.months.maxOf { it.size }, 1)
        Row(Modifier.fillMaxWidth()) {
            stats.months.forEachIndexed { index, titles ->
                val name = Month.of(index + 1).getDisplayName(TextStyle.FULL, Locale.UK)
                val chosen = month == index
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(2.dp))
                        .selectable(selected = chosen, enabled = titles.isNotEmpty(), role = Role.Button) { month = if (chosen) null else index }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$name: ${if (titles.isEmpty()) "none finished" else "${titles.size} ${if (titles.size == 1) "book" else "books"}"}"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(if (chosen) colors.rule.copy(alpha = 0.45f) else colors.paper)
                            .drawBehind { drawLine(colors.ruleStrong, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (titles.isNotEmpty()) {
                            val share = titles.size.toFloat() / most
                            Spacer(
                                Modifier
                                    .width(14.dp)
                                    .height((56 * share).roundToInt().coerceAtLeast(4).dp)
                                    .background(colors.accent, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                            )
                        }
                    }
                    Text(Month.of(index + 1).getDisplayName(TextStyle.NARROW, Locale.UK), style = Carrel.type.mono, color = colors.inkFaint)
                }
            }
        }
        month?.let { index ->
            val titles = stats.months[index]
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${Month.of(index + 1).getDisplayName(TextStyle.FULL, Locale.UK)} · ${titles.size} ${if (titles.size == 1) "book" else "books"}",
                    style = Carrel.type.mono,
                    color = colors.inkSoft,
                )
                titles.forEach { Text(it, style = Carrel.type.body, color = colors.ink) }
            }
        }
    }
}

/** A number above its label. */
@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = Carrel.type.heading, color = Carrel.colors.ink)
        Text(label, style = Carrel.type.mono, color = Carrel.colors.inkSoft)
    }
}
