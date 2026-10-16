package dev.vibe.gallery.data

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.ImageRequest
import coil.request.Options
import coil.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Uses Android's own thumbnail cache (MediaStore.loadThumbnail) for gallery grids. It is much
 * faster than decoding the full photo or a video frame, so tiles come back instantly when
 * scrolling. Falls through to Coil's normal decoding if the system can't provide a thumbnail.
 */
class MediaThumbFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val w = options.size.width.pxOrElse { 512 }.coerceAtLeast(64)
        val h = options.size.height.pxOrElse { 512 }.coerceAtLeast(64)
        try {
            val bmp = context.contentResolver.loadThumbnail(uri, android.util.Size(w, h), null)
            DrawableResult(
                drawable = BitmapDrawable(context.resources, bmp),
                isSampled = true,
                dataSource = DataSource.DISK,
            )
        } catch (e: Exception) {
            null
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (options.parameters.value<Boolean>("thumb") != true) return null
            if (data.authority != "media") return null
            return MediaThumbFetcher(context, data, options)
        }
    }
}

/** Request for a grid/cover thumbnail. */
fun thumbRequest(context: Context, uri: Uri, px: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(uri)
        .size(px)
        .setParameter("thumb", true)
        .build()
