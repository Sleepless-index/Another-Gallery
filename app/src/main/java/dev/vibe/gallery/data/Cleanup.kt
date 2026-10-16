package dev.vibe.gallery.data

import android.content.ContentResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.security.MessageDigest

/** A set of files with identical content. [items] are sorted oldest first (the one to keep). */
data class DupGroup(val items: List<MediaItem>) {
    val wastedBytes: Long get() = items.drop(1).sumOf { it.sizeBytes }
}

object Cleanup {

    private const val FULL_HASH_LIMIT = 64L * 1024 * 1024
    private const val EDGE = 2L * 1024 * 1024

    /**
     * Finds exact duplicates: same size, then same SHA-256. Files over 64 MB are compared by
     * size plus their first and last 2 MB, which is enough to tell real copies apart.
     */
    suspend fun findDuplicates(
        resolver: ContentResolver,
        items: List<MediaItem>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<DupGroup> = withContext(Dispatchers.IO) {
        val bySize = items.filter { it.sizeBytes > 0 }.groupBy { it.sizeBytes }.filterValues { it.size > 1 }
        val candidates = bySize.values.flatten()
        val total = candidates.size
        var done = 0
        val groups = ArrayList<DupGroup>()
        for ((_, sameSize) in bySize) {
            val byHash = HashMap<String, MutableList<MediaItem>>()
            for (item in sameSize) {
                val h = runCatching { hash(resolver, item) }.getOrNull()
                if (h != null) byHash.getOrPut(h) { ArrayList() } += item
                done++
                if (done % 4 == 0 || done == total) onProgress(done, total)
            }
            byHash.values.filter { it.size > 1 }.forEach { groups += DupGroup(it.sortedBy { m -> m.dateMs }) }
        }
        groups.sortedByDescending { it.wastedBytes }
    }

    private fun hash(resolver: ContentResolver, item: MediaItem): String? {
        val md = MessageDigest.getInstance("SHA-256")
        val pfd = resolver.openFileDescriptor(item.uri, "r") ?: return null
        pfd.use { fd ->
            FileInputStream(fd.fileDescriptor).use { input ->
                val buf = ByteArray(64 * 1024)
                val size = item.sizeBytes
                if (size <= FULL_HASH_LIMIT) {
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        md.update(buf, 0, n)
                    }
                } else {
                    var left = EDGE
                    while (left > 0) {
                        val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n < 0) break
                        md.update(buf, 0, n)
                        left -= n
                    }
                    input.channel.position(size - EDGE)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        md.update(buf, 0, n)
                    }
                }
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
