package dev.vibe.gallery.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import dev.vibe.gallery.data.MediaItem

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
