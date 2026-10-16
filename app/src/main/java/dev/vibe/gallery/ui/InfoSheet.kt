package dev.vibe.gallery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import dev.vibe.gallery.data.MediaItem
import dev.vibe.gallery.data.formatDuration
import dev.vibe.gallery.data.formatSize
import dev.vibe.gallery.ui.theme.GIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class InfoRow(val icon: ImageVector, val title: String, val value: String)

private class Info(val rows: List<InfoRow>)

fun copyToClipboard(context: Context, item: MediaItem) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newUri(context.contentResolver, item.name, item.uri))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(
    item: MediaItem,
    inVault: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onClipboard: () -> Unit,
    onHide: () -> Unit,
    onUseAs: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onEdit: () -> Unit,
    onPrint: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val info by produceState(Info(emptyList()), item.id) { value = readInfo(context, item) }
    val dateFmt = DateTimeFormatter.ofPattern("MMMM d, yyyy  •  h:mm a", Locale.getDefault())
    val whenText = dateFmt.format(Instant.ofEpochMilli(item.dateMs).atZone(ZoneId.systemDefault()))
    val card = RoundedCornerShape(26.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface,
        contentColor = cs.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header card: when it was taken
            Column(
                Modifier.fillMaxWidth().clip(card).background(cs.surfaceVariant).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(whenText, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
                Text(
                    listOfNotNull(
                        if (item.isVideo) "Video" else "Photo",
                        item.bucketName.takeIf { it.isNotBlank() },
                    ).joinToString("  •  "),
                    fontSize = 14.sp,
                    color = cs.onSurfaceVariant,
                )
            }

            // Detail rows
            Column(
                Modifier.fillMaxWidth().clip(card).background(cs.surfaceVariant).padding(vertical = 8.dp),
            ) {
                info.rows.forEach { InfoLine(it) }
            }

            // Actions
            val actions = buildList {
                add(Triple(GIcons.Share, "Share", onShare))
                if (!item.isVideo) add(Triple(GIcons.Clipboard, "Copy to clipboard", onClipboard))
                if (!inVault) add(Triple(GIcons.Lock, "Hide", onHide))
                if (!item.isVideo) add(Triple(GIcons.Image, "Use as", onUseAs))
                if (!inVault) {
                    add(Triple(GIcons.Copy, "Copy", onCopy))
                    add(Triple(GIcons.Move, "Move", onMove))
                }
                if (!item.isVideo) {
                    add(Triple(GIcons.Edit, "Edit", onEdit))
                    add(Triple(GIcons.Photos, "Print", onPrint))
                }
            }
            actions.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (icon, label, click) ->
                        Row(
                            Modifier
                                .weight(1f)
                                .height(58.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(cs.surfaceVariant)
                                .bounceClick(onClick = { onDismiss(); click() })
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(icon, null, tint = cs.onBackground, modifier = Modifier.size(22.dp))
                            Text(
                                label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                color = cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun InfoLine(row: InfoRow) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(cs.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(row.icon, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
            Text(row.value, fontSize = 14.sp, color = cs.onSurfaceVariant)
        }
    }
}

private fun openExif(context: Context, uri: android.net.Uri): ExifInterface? =
    context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }

private suspend fun readInfo(context: Context, item: MediaItem): Info =
    withContext(Dispatchers.IO) {
        val rows = mutableListOf<InfoRow>()
        rows += InfoRow(GIcons.Image, "Label", item.name)

        // Real on-disk path (e.g. /storage/XXXX-XXXX/Download/...), falling back to the folder.
        val path = if (item.id >= 0) {
            runCatching {
                context.contentResolver.query(item.uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull()
        } else null
        val folder = path?.substringBeforeLast('/')
            ?: ("/storage/emulated/0/" + item.relativePath).trimEnd('/')
        rows += InfoRow(GIcons.Info, "Path", if (item.id < 0) "Vault (private to this app)" else folder)

        var ppi: String? = null
        var exif: ExifInterface? = null
        if (!item.isVideo && item.id >= 0) {
            exif = runCatching { openExif(context, MediaStore.setRequireOriginal(item.uri)) }.getOrNull()
                ?: runCatching { openExif(context, item.uri) }.getOrNull()
            val x = exif?.getAttributeDouble(ExifInterface.TAG_X_RESOLUTION, 0.0) ?: 0.0
            val y = exif?.getAttributeDouble(ExifInterface.TAG_Y_RESOLUTION, 0.0) ?: 0.0
            if (x > 0 && y > 0) ppi = "%.1f × %.1f PPI".format(x, y)
        }

        val dim = mutableListOf<String>()
        if (item.width > 0 && item.height > 0) {
            dim += "${item.width} × ${item.height}"
            val mp = item.width.toLong() * item.height / 1_000_000.0
            if (!item.isVideo && mp >= 0.1) dim += "%.1f MP".format(mp)
        }
        ppi?.let { dim += it }
        if (item.isVideo && item.durationMs > 0) dim += formatDuration(item.durationMs)
        dim += formatSize(item.sizeBytes)
        rows += InfoRow(GIcons.Photos, "Dimensions", dim.joinToString("  •  "))

        if (exif != null) {
            val camera = listOfNotNull(
                exif.getAttribute(ExifInterface.TAG_MAKE),
                exif.getAttribute(ExifInterface.TAG_MODEL),
            ).joinToString(" ").trim()
            if (camera.isNotEmpty()) rows += InfoRow(GIcons.Image, "Camera", camera)
            val parts = mutableListOf<String>()
            exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { parts += "f/$it" }
            exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()?.let {
                parts += if (it < 1.0 && it > 0.0) "1/${(1.0 / it).toInt()}s" else "${it}s"
            }
            exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { parts += "ISO $it" }
            if (parts.isNotEmpty()) rows += InfoRow(GIcons.Brightness, "Exposure", parts.joinToString("  •  "))
            val ll = exif.latLong
            if (ll != null) rows += InfoRow(GIcons.Info, "Location", "%.5f, %.5f".format(ll[0], ll[1]))
        }
        if (item.mime.isNotBlank()) rows += InfoRow(GIcons.Info, "Type", item.mime)
        Info(rows)
    }
