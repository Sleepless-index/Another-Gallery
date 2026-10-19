package sleepless_index.haze.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.random.Random

/**
 * Private storage inside the app's own folder (not visible to other apps or the system gallery).
 * Items get negative ids so they never clash with MediaStore ids.
 */
class VaultStore(private val context: Context) {

    private val dir = File(context.filesDir, "vault").apply { mkdirs() }
    private val index = File(dir, "index.json")

    private data class Entry(
        val id: Long,
        val file: String,
        val name: String,
        val mime: String,
        val isVideo: Boolean,
        val dateMs: Long,
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val sizeBytes: Long,
        val origDir: String,
    )

    private fun readAll(): MutableList<Entry> {
        if (!index.exists()) return mutableListOf()
        return runCatching {
            val arr = JSONArray(index.readText())
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    id = o.getLong("id"),
                    file = o.getString("file"),
                    name = o.getString("name"),
                    mime = o.optString("mime"),
                    isVideo = o.optBoolean("video"),
                    dateMs = o.optLong("date"),
                    width = o.optInt("w"),
                    height = o.optInt("h"),
                    durationMs = o.optLong("dur"),
                    sizeBytes = o.optLong("size"),
                    origDir = o.optString("dir"),
                )
            }
        }.getOrDefault(mutableListOf())
    }

    private fun writeAll(list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id).put("file", e.file).put("name", e.name).put("mime", e.mime)
                    .put("video", e.isVideo).put("date", e.dateMs).put("w", e.width).put("h", e.height)
                    .put("dur", e.durationMs).put("size", e.sizeBytes).put("dir", e.origDir)
            )
        }
        index.writeText(arr.toString())
    }

    private fun Entry.toItem() = MediaItem(
        id = id,
        uri = Uri.fromFile(File(dir, file)),
        name = name,
        mime = mime,
        isVideo = isVideo,
        dateMs = dateMs,
        width = width,
        height = height,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        bucketId = Long.MIN_VALUE,
        bucketName = "Vault",
        relativePath = origDir,
    )

    suspend fun list(): List<MediaItem> = withContext(Dispatchers.IO) {
        readAll().filter { File(dir, it.file).exists() }.map { it.toItem() }.sortedByDescending { it.dateMs }
    }

    /** Copies items into the vault. Returns the originals that were stored. */
    suspend fun add(items: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        val all = readAll()
        val added = ArrayList<MediaItem>()
        for (item in items) {
            val fileName = UUID.randomUUID().toString()
            val target = File(dir, fileName)
            val ok = runCatching {
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                } != null
            }.getOrDefault(false)
            if (!ok || !target.exists()) {
                target.delete()
                continue
            }
            all += Entry(
                id = -(Random.nextLong(1L, Long.MAX_VALUE / 2)),
                file = fileName,
                name = item.name,
                mime = item.mime,
                isVideo = item.isVideo,
                dateMs = item.dateMs,
                width = item.width,
                height = item.height,
                durationMs = item.durationMs,
                sizeBytes = target.length(),
                origDir = item.relativePath,
            )
            added += item
        }
        writeAll(all)
        added
    }

    /** Moves vault items back into the normal gallery. Returns how many were restored. */
    suspend fun restore(items: List<MediaItem>): Int = withContext(Dispatchers.IO) {
        val all = readAll()
        var n = 0
        for (item in items) {
            val e = all.firstOrNull { it.id == item.id } ?: continue
            val f = File(dir, e.file)
            val dest = if (e.origDir.startsWith("Pictures/") || e.origDir.startsWith("DCIM/") ||
                e.origDir.startsWith("Movies/")
            ) e.origDir else "Pictures/Restored/"
            val uri = runCatching {
                f.inputStream().use { MediaOps.insert(context, it, e.name, e.mime, e.isVideo, e.dateMs, dest) }
            }.getOrNull()
            if (uri != null) {
                f.delete()
                all.remove(e)
                n++
            }
        }
        writeAll(all)
        n
    }

    suspend fun delete(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        val all = readAll()
        val ids = items.map { it.id }.toSet()
        all.filter { it.id in ids }.forEach { File(dir, it.file).delete() }
        writeAll(all.filter { it.id !in ids })
    }
}
