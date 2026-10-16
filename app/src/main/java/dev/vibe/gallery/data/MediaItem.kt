package dev.vibe.gallery.data

import android.net.Uri
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val dateMs: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
) {
    /** width / height, clamped so masonry cards never get absurdly tall or wide. */
    val aspect: Float
        get() {
            val a = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f
            return a.coerceIn(0.55f, 1.8f)
        }
}

data class Album(
    val bucketId: Long,
    val name: String,
    val count: Int,
    val cover: MediaItem,
)

/** One row of the photo list: either a date header or a media tile. */
sealed interface Entry {
    val key: String
    val dateMs: Long

    data class Header(
        val title: String,
        val sub: String,
        override val dateMs: Long,
        val epochDay: Long,
    ) : Entry {
        override val key: String get() = "h$epochDay"
    }

    data class Media(val item: MediaItem) : Entry {
        override val key: String get() = "m${item.id}"
        override val dateMs: Long get() = item.dateMs
    }
}

fun buildEntries(items: List<MediaItem>): List<Entry> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    val dayYearFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
    val out = ArrayList<Entry>(items.size + 64)
    var lastDay: LocalDate? = null
    for (m in items) {
        val d = Instant.ofEpochMilli(m.dateMs).atZone(zone).toLocalDate()
        if (d != lastDay) {
            lastDay = d
            val full = d.format(if (d.year == today.year) dayFmt else dayYearFmt)
            val header = when (d) {
                today -> Entry.Header("Today", full, m.dateMs, d.toEpochDay())
                today.minusDays(1) -> Entry.Header("Yesterday", full, m.dateMs, d.toEpochDay())
                else -> Entry.Header(full, "", m.dateMs, d.toEpochDay())
            }
            out.add(header)
        }
        out.add(Entry.Media(m))
    }
    return out
}

/**
 * Masonry helper: landscape items span the whole row, which would leave a hole under the
 * shorter column. Before each landscape item, pull later portrait items forward (within the
 * same day) until the two columns are about even.
 */
fun reorderForMasonry(entries: List<Entry>): List<Entry> {
    val out = ArrayList<Entry>(entries.size)
    var i = 0
    while (i < entries.size) {
        val e = entries[i]
        if (e is Entry.Header) {
            out.add(e)
            i++
            continue
        }
        val segment = ArrayList<Entry.Media>()
        while (i < entries.size && entries[i] is Entry.Media) {
            segment.add(entries[i] as Entry.Media)
            i++
        }
        var left = 0f
        var right = 0f
        val pending = ArrayList(segment)
        while (pending.isNotEmpty()) {
            val next = pending[0]
            val wide = next.item.aspect >= 1.15f
            if (!wide) {
                pending.removeAt(0)
                out.add(next)
                val h = 1f / next.item.aspect
                if (left <= right) left += h else right += h
            } else if (kotlin.math.abs(left - right) > 0.3f && pending.any { it.item.aspect < 1.15f }) {
                val idx = pending.indexOfFirst { it.item.aspect < 1.15f }
                val pulled = pending.removeAt(idx)
                out.add(pulled)
                val h = 1f / pulled.item.aspect
                if (left <= right) left += h else right += h
            } else {
                pending.removeAt(0)
                out.add(next)
                val top = maxOf(left, right) + 1f / next.item.aspect
                left = top
                right = top
            }
        }
    }
    return out
}

fun buildAlbums(items: List<MediaItem>): List<Album> {
    val groups = LinkedHashMap<Long, MutableList<MediaItem>>()
    for (m in items) groups.getOrPut(m.bucketId) { ArrayList() }.add(m)
    return groups.values
        .map { list -> Album(list[0].bucketId, list[0].bucketName.ifBlank { "Unknown" }, list.size, list[0]) }
        .sortedByDescending { it.count }
}

private val monthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

fun monthLabel(ms: Long): String =
    monthFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
