package dev.vibe.gallery.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object MediaOps {

    /** Where a brand-new album folder is created. */
    fun newAlbumDir(name: String): String {
        val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "New album" }
        return "Pictures/$clean/"
    }

    /** Folder an existing album lives in (kept inside Pictures/ or DCIM/, which MediaStore allows). */
    fun albumDir(album: Album): String {
        val rp = album.cover.relativePath
        return if (rp.startsWith("Pictures/") || rp.startsWith("DCIM/")) rp else newAlbumDir(album.name)
    }

    /**
     * Copies items into [relativeDir] as new MediaStore entries. Originals are never touched.
     * Returns the items that were copied successfully.
     */
    suspend fun copyItems(context: Context, items: List<MediaItem>, relativeDir: String): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val done = ArrayList<MediaItem>()
            for (item in items) {
                val ok = runCatching {
                    context.contentResolver.openInputStream(item.uri)?.use { input ->
                        insert(context, input, item.name, item.mime, item.isVideo, item.dateMs, relativeDir)
                    } != null
                }.getOrDefault(false)
                if (ok) done += item
            }
            done
        }

    /** Writes [input] into MediaStore. Videos fall back to Movies/ if Pictures/ is refused. */
    fun insert(
        context: Context,
        input: InputStream,
        name: String,
        mime: String,
        isVideo: Boolean,
        dateMs: Long,
        relativeDir: String,
    ): Uri? {
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val dirs = if (isVideo && relativeDir.startsWith("Pictures/")) {
            listOf(relativeDir, "Movies/" + relativeDir.removePrefix("Pictures/"))
        } else {
            listOf(relativeDir)
        }
        for (dir in dirs) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name.ifBlank { if (isVideo) "video.mp4" else "image.jpg" })
                if (mime.isNotBlank()) put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (dir.endsWith("/")) dir else "$dir/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (dateMs > 0) put(MediaStore.MediaColumns.DATE_TAKEN, dateMs)
            }
            val uri = try {
                resolver.insert(collection, values)
            } catch (e: IllegalArgumentException) {
                null
            } ?: continue
            try {
                resolver.openOutputStream(uri)?.use { out -> input.copyTo(out) }
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                return uri
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
        }
        return null
    }
}
