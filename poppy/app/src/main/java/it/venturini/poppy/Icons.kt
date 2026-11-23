package it.venturini.poppy

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun line(name: String, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    for (d in paths) {
        b.addPath(
            PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }
    return b.build()
}

/** Le stesse icone a linea della webapp. */
object Ico {
    val bottle = line(
        "bottle",
        "M10 5c0-1.6.9-3 2-3s2 1.4 2 3",
        "M9 5h6a1 1 0 0 1 1 1v1a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z",
        "M8.5 8h7v10.5A3.5 3.5 0 0 1 12 22a3.5 3.5 0 0 1-3.5-3.5z",
        "M8.5 12.5h3M8.5 16h3"
    )
    val drop = line("drop", "M12 2.8s6 6.4 6 11.1a6 6 0 0 1-12 0C6 9.2 12 2.8 12 2.8z", "M9.2 14.5a2.9 2.9 0 0 0 2.6 2.7")
    val moon = line("moon", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z")
    val pen = line("pen", "M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z")
    val heart = line("heart", "M19.5 13.6 12 21l-7.5-7.4A5 5 0 1 1 12 7a5 5 0 1 1 7.5 6.6z")
    val diaper = line("diaper", "M3 6h18v4c0 5.5-4 10-9 10S3 15.5 3 10z", "M3 10h18")
}
