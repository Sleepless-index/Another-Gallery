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
    /** When the item was moved to the trash (0 if unknown / not trashed). */
    val trashedMs: Long = 0L,
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
