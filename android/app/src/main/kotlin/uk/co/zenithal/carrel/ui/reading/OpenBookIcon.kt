package uk.co.zenithal.carrel.ui.reading

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** An open book, in outline, for the Home button that opens reading (Material's core icons have none). */
val OpenBookIcon: ImageVector by lazy {
    ImageVector.Builder("OpenBook", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f, strokeLineJoin = StrokeJoin.Round) {
            // The left page, the right page, then the spine between them.
            moveTo(12f, 6.5f)
            curveTo(10.5f, 5.2f, 8.2f, 4.5f, 5.5f, 4.5f)
            curveTo(4.4f, 4.5f, 3.3f, 4.65f, 2.5f, 4.9f)
            verticalLineTo(19f)
            curveTo(3.3f, 18.75f, 4.4f, 18.6f, 5.5f, 18.6f)
            curveTo(8.2f, 18.6f, 10.5f, 19.3f, 12f, 20.6f)
            curveTo(13.5f, 19.3f, 15.8f, 18.6f, 18.5f, 18.6f)
            curveTo(19.6f, 18.6f, 20.7f, 18.75f, 21.5f, 19f)
            verticalLineTo(4.9f)
            curveTo(20.7f, 4.65f, 19.6f, 4.5f, 18.5f, 4.5f)
            curveTo(15.8f, 4.5f, 13.5f, 5.2f, 12f, 6.5f)
            close()
            moveTo(12f, 6.5f)
            verticalLineTo(20.6f)
        }
    }.build()
}
