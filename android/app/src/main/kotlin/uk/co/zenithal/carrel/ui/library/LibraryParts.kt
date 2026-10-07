package uk.co.zenithal.carrel.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import uk.co.zenithal.carrel.data.plainNumber
import uk.co.zenithal.carrel.ui.theme.Carrel
import kotlin.math.cos
import kotlin.math.sin

/**
 * Half-star rating. Each star has two tap areas (left half, right half); choosing the current rating clears it. The
 * stars are larger than the website's, so a half is still a fair target for a finger.
 */
@Composable
fun StarRating(value: Double?, onChange: (Double?) -> Unit, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (enabled) 1f else 0.6f)) {
        (1..5).forEach { star ->
            Box(Modifier.size(38.dp)) {
                Star(fill(value, star), 38.dp, Modifier.padding(2.dp))
                Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                    listOf(star - 0.5, star.toDouble()).forEach { rating ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .selectable(selected = value == rating, enabled = enabled, role = Role.RadioButton) {
                                    onChange(if (value == rating) null else rating)
                                }
                                .semantics { contentDescription = "${plainNumber(rating)} ${if (rating == 1.0) "star" else "stars"}" },
                        )
                    }
                }
            }
        }
        Text(
            value?.let { "%.1f".format(it) } ?: "Not rated",
            style = Carrel.type.mono,
            color = Carrel.colors.inkFaint,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** A read-only rating for shelves. */
@Composable
fun StarDisplay(value: Double, modifier: Modifier = Modifier, size: Dp = 13.dp) {
    Row(modifier.semantics { contentDescription = "Rated ${plainNumber(value)} out of 5" }) {
        (1..5).forEach { star -> Star(fill(value, star), size) }
    }
}

/** How much of a star a rating fills: all, half, or none. */
private fun fill(value: Double?, star: Int): Float = when {
    value == null -> 0f
    value >= star -> 1f
    value >= star - 0.5 -> 0.5f
    else -> 0f
}

/** A five-pointed star, outlined in the rule colour and filled with the accent from the left by `fill`. */
@Composable
private fun Star(fill: Float, size: Dp, modifier: Modifier = Modifier) {
    val empty = Carrel.colors.ruleStrong
    val full = Carrel.colors.accent
    Canvas(modifier.size(size)) {
        val path = starPath(this.size.minDimension)
        drawPath(path, empty)
        if (fill > 0f) clipRect(right = this.size.width * fill) { drawPath(path, full) }
    }
}

private fun starPath(side: Float): Path {
    val centre = Offset(side / 2, side * 0.54f)
    val outer = side * 0.5f
    val inner = outer * 0.42f
    return Path().apply {
        for (i in 0 until 10) {
            val angle = Math.toRadians(-90.0 + i * 36.0)
            val r = if (i % 2 == 0) outer else inner
            val point = Offset(centre.x + r * cos(angle).toFloat(), centre.y + r * sin(angle).toFloat())
            if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        close()
    }
}

/** A thin bar filled with the accent to the given percentage. */
@Composable
fun ProgressBar(percent: Double?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(3.dp)
    Box(modifier.fillMaxWidth().height(6.dp).background(Carrel.colors.rule, shape)) {
        val share = ((percent ?: 0.0) / 100).coerceIn(0.0, 1.0).toFloat()
        if (share > 0f) Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(Carrel.colors.accent, shape))
    }
}

/**
 * One choice from a few, as bordered mono labels with the chosen one filled: reading status, progress unit, and the
 * shelf view. They wrap onto more lines on a narrow screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Choices(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Carrel.colors
    FlowRow(modifier.alpha(if (enabled) 1f else 0.6f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val chosen = value == selected
            Text(
                label,
                style = Carrel.type.mono.copy(letterSpacing = 0.04.em),
                color = if (chosen) colors.onAccent else colors.inkSoft,
                modifier = Modifier
                    .border(1.dp, if (chosen) colors.accent else colors.ruleStrong, RoundedCornerShape(2.dp))
                    .background(if (chosen) colors.accent else Color.Transparent, RoundedCornerShape(2.dp))
                    .selectable(selected = chosen, enabled = enabled, role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** A mono label in capitals, like the website's .shelf-label. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Carrel.type.mono.copy(letterSpacing = 0.1.em), color = Carrel.colors.inkSoft, modifier = modifier)
}
