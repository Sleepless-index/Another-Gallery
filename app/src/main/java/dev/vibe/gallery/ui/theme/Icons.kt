package dev.vibe.gallery.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Icons drawn from the approved mockup (24x24 viewport, rounded strokes).

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M ${cx - r} $cy a $r $r 0 1 0 ${2 * r} 0 a $r $r 0 1 0 ${-2 * r} 0 z"

private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
    "M ${x + r} $y h ${w - 2 * r} a $r $r 0 0 1 $r $r v ${h - 2 * r} " +
        "a $r $r 0 0 1 ${-r} $r h ${-(w - 2 * r)} a $r $r 0 0 1 ${-r} ${-r} " +
        "v ${-(h - 2 * r)} a $r $r 0 0 1 $r ${-r} z"

private class IconBuilder(name: String) {
    private val b = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

    fun s(d: String): IconBuilder {
        b.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        return this
    }

    fun f(d: String): IconBuilder {
        b.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = SolidColor(Color.Black),
        )
        return this
    }

    fun build(): ImageVector = b.build()
}

private const val HEART =
    "M 12 20.5 s -7.5 -4.6 -7.5 -10.4 A 4.2 4.2 0 0 1 12 7.6 a 4.2 4.2 0 0 1 7.5 2.5 c 0 5.8 -7.5 10.4 -7.5 10.4 z"

object GIcons {
    val Photos: ImageVector by lazy {
        IconBuilder("Photos").s(rrect(3f, 4f, 18f, 16f, 3.5f)).s(circle(9f, 10f, 1.6f))
            .s("M 21 16 l -5 -5 l -8 8").build()
    }
    val Albums: ImageVector by lazy {
        IconBuilder("Albums").s("M 6 4 h 12").s(rrect(3f, 7f, 18f, 13f, 3.5f)).build()
    }
    val Search: ImageVector by lazy {
        IconBuilder("Search").s(circle(11f, 11f, 6.5f)).s("M 20 20 l -4 -4").build()
    }
    val Settings: ImageVector by lazy {
        IconBuilder("Settings").s("M 4 7 h 10 M 18 7 h 2 M 4 17 h 2 M 10 17 h 10")
            .s(circle(16f, 7f, 2f)).s(circle(8f, 17f, 2f)).build()
    }
    val Heart: ImageVector by lazy { IconBuilder("Heart").s(HEART).build() }
    val HeartFilled: ImageVector by lazy { IconBuilder("HeartFilled").f(HEART).build() }
    val Share: ImageVector by lazy {
        IconBuilder("Share").s("M 12 15 V 4 M 8 8 l 4 -4 l 4 4")
            .s("M 5 12 v 6 a 2 2 0 0 0 2 2 h 10 a 2 2 0 0 0 2 -2 v -6").build()
    }
    val Trash: ImageVector by lazy {
        IconBuilder("Trash").s("M 5 7 h 14 M 10 7 V 4 h 4 v 3 M 7 7 l 1 13 h 8 l 1 -13").build()
    }
    val Back: ImageVector by lazy { IconBuilder("Back").s("M 15 5 l -7 7 l 7 7").build() }
    val More: ImageVector by lazy {
        IconBuilder("More").f(circle(12f, 5.5f, 1.7f)).f(circle(12f, 12f, 1.7f)).f(circle(12f, 18.5f, 1.7f)).build()
    }
    val CheckCircle: ImageVector by lazy {
        IconBuilder("CheckCircle").s(circle(12f, 12f, 8.5f)).s("M 8.5 12.3 l 2.4 2.4 l 4.6 -5").build()
    }
    val Check: ImageVector by lazy { IconBuilder("Check").s("M 5 12.5 l 4.5 4.5 L 19 7.5").build() }
    val Close: ImageVector by lazy { IconBuilder("Close").s("M 6 6 l 12 12 M 18 6 L 6 18").build() }
    val Plus: ImageVector by lazy { IconBuilder("Plus").s("M 12 5 v 14 M 5 12 h 14").build() }
    val Minus: ImageVector by lazy { IconBuilder("Minus").s("M 5 12 h 14").build() }
    val Play: ImageVector by lazy { IconBuilder("Play").f("M 7 4 v 16 l 13 -8 z").build() }
    val Pause: ImageVector by lazy {
        IconBuilder("Pause").f(rrect(6f, 5f, 4.2f, 14f, 1.2f)).f(rrect(13.8f, 5f, 4.2f, 14f, 1.2f)).build()
    }
    val Lock: ImageVector by lazy {
        IconBuilder("Lock").s(rrect(5f, 11f, 14f, 9f, 2.5f)).s("M 8 11 V 8 a 4 4 0 0 1 8 0 v 3").build()
    }
    val Info: ImageVector by lazy {
        IconBuilder("Info").s(circle(12f, 12f, 9f)).s("M 12 11 v 6 M 12 7.5 v 0.5").build()
    }
    val Rotate: ImageVector by lazy {
        IconBuilder("Rotate").s("M 20 12 a 8 8 0 1 1 -2.4 -5.7").s("M 20 4 v 5 h -5").build()
    }
    val RotateLeft: ImageVector by lazy {
        IconBuilder("RotateLeft").s("M 4 12 a 8 8 0 1 0 2.4 -5.7").s("M 4 4 v 5 h 5").build()
    }
    val Flip: ImageVector by lazy {
        IconBuilder("Flip").s("M 12 4 v 16").s("M 8 7 L 3 18 h 5 z M 16 7 l 5 11 h -5 z").build()
    }
    val Pip: ImageVector by lazy {
        IconBuilder("Pip").s(rrect(3f, 5f, 18f, 14f, 3f)).s(rrect(12f, 11f, 6f, 4f, 1f)).build()
    }
    val Subtitles: ImageVector by lazy {
        IconBuilder("Subtitles").s(rrect(3f, 5f, 18f, 14f, 3f)).s("M 7 11 h 3 M 13 11 h 4 M 7 15 h 5 M 15 15 h 2").build()
    }
    val Fit: ImageVector by lazy {
        IconBuilder("Fit").s("M 4 9 V 5 h 4 M 20 9 V 5 h -4 M 4 15 v 4 h 4 M 20 15 v 4 h -4").build()
    }
    val Audio: ImageVector by lazy {
        IconBuilder("Audio").s("M 4 10 v 4 M 8 7 v 10 M 12 4 v 16 M 16 8 v 8 M 20 11 v 2").build()
    }
    val VideoAlbum: ImageVector by lazy {
        IconBuilder("VideoAlbum").s(rrect(3f, 6f, 18f, 12f, 3f)).f("M 10.5 9.5 v 5 l 4 -2.5 z").build()
    }
    val Brightness: ImageVector by lazy {
        IconBuilder("Brightness").s(circle(12f, 12f, 4f))
            .s("M 12 3 v 2 M 12 19 v 2 M 3 12 h 2 M 19 12 h 2 M 5.6 5.6 l 1.4 1.4 M 17 17 l 1.4 1.4 M 18.4 5.6 L 17 7 M 7 17 l -1.4 1.4")
            .build()
    }
    val Volume: ImageVector by lazy {
        IconBuilder("Volume").s("M 4 9.5 h 3.5 L 12 5.5 v 13 L 7.5 14.5 H 4 z")
            .s("M 15.5 9 a 4 4 0 0 1 0 6 M 18 6.5 a 7.5 7.5 0 0 1 0 11").build()
    }
    val Restore: ImageVector by lazy {
        IconBuilder("Restore").s("M 4 12 a 8 8 0 1 0 2.4 -5.7").s("M 4 4 v 5 h 5").s("M 12 8 v 4 l 3 2").build()
    }
    val Image: ImageVector by lazy {
        IconBuilder("Image").s(rrect(3f, 4f, 18f, 16f, 3.5f)).s(circle(9f, 10f, 1.6f)).s("M 21 16 l -5 -5 l -8 8").build()
    }
}
