package sleepless_index.haze.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pure bitmap helpers for the editor (no Compose types in here). */
object ImageOps {

    class Filter(val name: String, val matrix: FloatArray?)

    private fun make(
        sat: Float = 1f,
        contrast: Float = 1f,
        bright: Float = 0f,
        r: Float = 1f,
        g: Float = 1f,
        b: Float = 1f,
    ): ColorMatrix {
        val m = ColorMatrix()
        m.setSaturation(sat)
        val t = (1f - contrast) * 128f + bright
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast * r, 0f, 0f, 0f, t,
                    0f, contrast * g, 0f, 0f, t,
                    0f, 0f, contrast * b, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        return m
    }

    val filters: List<Filter> = listOf(
        Filter("Original", null),
        Filter("Mono", make(sat = 0f).array.copyOf()),
        Filter("Noir", make(sat = 0f, contrast = 1.3f, bright = -8f).array.copyOf()),
        Filter("Vivid", make(sat = 1.5f, contrast = 1.1f).array.copyOf()),
        Filter("Fade", make(sat = 0.75f, contrast = 0.85f, bright = 18f).array.copyOf()),
        Filter("Warm", make(sat = 1.05f, r = 1.1f, b = 0.88f).array.copyOf()),
        Filter("Cool", make(sat = 1.05f, r = 0.9f, b = 1.1f).array.copyOf()),
        Filter(
            "Sepia",
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        ),
        Filter("Drama", make(sat = 0.8f, contrast = 1.4f, bright = -10f).array.copyOf()),
    )

    /** Final 4x5 color matrix: chosen filter, then the manual adjustments on top. */
    fun colorMatrix(
        brightness: Float,
        contrast: Float,
        saturation: Float,
        warmth: Float,
        filterIdx: Int,
    ): FloatArray {
        val m = ColorMatrix()
        filters.getOrNull(filterIdx)?.matrix?.let { m.set(it) }
        m.postConcat(
            make(
                sat = saturation,
                contrast = contrast,
                bright = brightness * 90f,
                r = 1f + 0.16f * warmth,
                g = 1f + 0.03f * warmth,
                b = 1f - 0.16f * warmth,
            )
        )
        return m.array.copyOf()
    }

    fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val longest = max(w, h)
            if (longest > maxSide) {
                val s = maxSide.toFloat() / longest
                decoder.setTargetSize(
                    (w * s).roundToInt().coerceAtLeast(1),
                    (h * s).roundToInt().coerceAtLeast(1),
                )
            }
        }
    }

    /** Zoom needed so an image rotated by [deg] still covers a w x h frame (no empty corners). */
    fun coverScale(w: Int, h: Int, deg: Float): Float {
        val rad = Math.toRadians(abs(deg).toDouble())
        val ratio = max(w.toFloat() / h, h.toFloat() / w)
        return (cos(rad) + sin(rad) * ratio).toFloat().coerceAtLeast(1f)
    }

    /** Applies quarter turns, horizontal flip, then straighten. Output keeps the rotated frame size. */
    fun transform(src: Bitmap, quarter: Int, flip: Boolean, straighten: Float): Bitmap {
        val q = ((quarter % 4) + 4) % 4
        if (q == 0 && !flip && abs(straighten) < 0.01f) return src
        val sw = src.width
        val sh = src.height
        val ow = if (q % 2 == 0) sw else sh
        val oh = if (q % 2 == 0) sh else sw
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val m = Matrix()
        m.postTranslate(-sw / 2f, -sh / 2f)
        m.postRotate(q * 90f)
        if (flip) m.postScale(-1f, 1f)
        m.postRotate(straighten)
        val s = coverScale(ow, oh, straighten)
        m.postScale(s, s)
        m.postTranslate(ow / 2f, oh / 2f)
        canvas.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /**
     * Renders the edits at (capped) full resolution and stores a NEW image in Pictures/Edited.
     * The original is never touched. [crop] is l, t, r, b in 0..1 of the transformed image.
     */
    fun exportCopy(
        context: Context,
        sourceUri: Uri,
        displayName: String,
        mime: String,
        dateMs: Long,
        quarter: Int,
        flip: Boolean,
        straighten: Float,
        crop: FloatArray,
        matrix: FloatArray,
    ): Uri? {
        val full = decode(context, sourceUri, 4096)
        val transformed = transform(full, quarter, flip, straighten)

        val x = (crop[0] * transformed.width).roundToInt().coerceIn(0, transformed.width - 1)
        val y = (crop[1] * transformed.height).roundToInt().coerceIn(0, transformed.height - 1)
        val cw = ((crop[2] - crop[0]) * transformed.width).roundToInt().coerceIn(1, transformed.width - x)
        val ch = ((crop[3] - crop[1]) * transformed.height).roundToInt().coerceIn(1, transformed.height - y)

        val out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        }
        Canvas(out).drawBitmap(transformed, Rect(x, y, x + cw, y + ch), Rect(0, 0, cw, ch), paint)

        val png = mime.contains("png", ignoreCase = true)
        val base = displayName.substringBeforeLast('.', displayName)
        val name = "${base}_edit_${System.currentTimeMillis()}." + if (png) "png" else "jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, if (png) "image/png" else "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Edited")
            put(MediaStore.Images.Media.DATE_TAKEN, dateMs)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            val stream = resolver.openOutputStream(uri) ?: throw IllegalStateException("no stream")
            stream.use {
                out.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it)
            }
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }
}
