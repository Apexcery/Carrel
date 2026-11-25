package uk.co.zenithal.carrel.ui.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A die showing five, as the website's (after Lucide's "dice-5"), for the search box's random book button. */
val DiceIcon: ImageVector by lazy {
    ImageVector.Builder("Dice", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(5f, 3f)
            horizontalLineTo(19f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21f, 5f)
            verticalLineTo(19f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 21f)
            horizontalLineTo(5f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 19f)
            verticalLineTo(5f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 3f)
            close()
        }
        // The pips, as filled circles.
        for ((x, y) in listOf(8f to 8f, 16f to 8f, 12f to 12f, 8f to 16f, 16f to 16f)) {
            path(fill = SolidColor(Color.Black)) {
                moveTo(x - 1.5f, y)
                arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x + 1.5f, y)
                arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x - 1.5f, y)
                close()
            }
        }
    }.build()
}
