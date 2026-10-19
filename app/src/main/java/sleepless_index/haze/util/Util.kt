package sleepless_index.haze.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import sleepless_index.haze.data.MediaItem

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun shareItems(context: Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    val intent = if (items.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = items[0].mime.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, items[0].uri)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = when {
                items.all { it.isVideo } -> "video/*"
                items.none { it.isVideo } -> "image/*"
                else -> "*/*"
            }
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}

/**
 * Reverse image search without giving Haze network access: hands the picture to Google Lens if it
 * is installed, otherwise to whatever app the user picks from the share sheet.
 */
fun searchImage(context: Context, item: MediaItem) {
    if (item.isVideo) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = item.mime.ifBlank { "image/*" }
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val lens = Intent(send).apply {
        setClassName(
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.search.lens.LensShareEntryPointActivity",
        )
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val opened = try {
        context.startActivity(lens)
        true
    } catch (e: Exception) {
        false
    }
    if (!opened) {
        context.startActivity(
            Intent.createChooser(send, "Search image with…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
