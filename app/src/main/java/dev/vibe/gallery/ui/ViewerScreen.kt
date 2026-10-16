package dev.vibe.gallery.ui

import android.content.Context
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.vibe.gallery.data.MediaItem
import dev.vibe.gallery.data.formatDuration
import dev.vibe.gallery.data.formatSize
import dev.vibe.gallery.ui.theme.GIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ChromeBg = Color(0xCC141414)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    vm: GalleryViewModel,
    startIndex: Int,
    onBack: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onEdit: (MediaItem) -> Unit,
) {
    val list by vm.viewerList.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()

    if (list.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, list.lastIndex)) { list.size }
    var chrome by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val current = list[pagerState.currentPage.coerceIn(0, list.lastIndex)]
    val isFav = current.id in favs

    val dateFmt = remember { DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }
    val zoned = Instant.ofEpochMilli(current.dateMs).atZone(ZoneId.systemDefault())

    val topBarH = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    val bottomBarH = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 18.dp + 64.dp + 12.dp
    val insetTop by animateDpAsState(if (chrome) topBarH else 0.dp, tween(220), label = "insetTop")
    val insetBottom by animateDpAsState(if (chrome) bottomBarH else 0.dp, tween(220), label = "insetBottom")

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1,
            key = { index -> list.getOrNull(index)?.id ?: index.toLong() },
        ) { page ->
            val item = list[page]
            if (item.isVideo) {
                VideoPage(
                    item = item,
                    insetTop = insetTop,
                    insetBottom = insetBottom,
                    onTap = { chrome = !chrome },
                    onPlay = { onPlay(item) },
                )
            } else {
                ZoomableImage(
                    item = item,
                    onTap = { chrome = !chrome },
                    insetTop = insetTop,
                    insetBottom = insetBottom,
                    onSwipeDown = onBack,
                )
            }
        }

        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 2 },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GIconButton(GIcons.Back, "Back", onClick = onBack, container = ChromeBg, tint = Color.White)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = dateFmt.format(zoned),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = timeFmt.format(zoned),
                        color = Color(0xFFA8A8AC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Box {
                    GIconButton(GIcons.More, "More options", onClick = { menu = true }, container = ChromeBg, tint = Color.White)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Details") },
                            onClick = {
                                menu = false
                                showInfo = true
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PillBar {
                    PillItem(
                        icon = GIcons.Share, label = "Share", selected = false,
                        onClick = { dev.vibe.gallery.util.shareItems(context, listOf(current)) },
                        modifier = Modifier.weight(1f),
                    )
                    PillItem(
                        icon = if (isFav) GIcons.HeartFilled else GIcons.Heart,
                        label = "Favorite",
                        selected = isFav,
                        onClick = { vm.toggleFavorite(listOf(current.id)) },
                        modifier = Modifier.weight(1f),
                    )
                    if (current.isVideo) {
                        PillItem(
                            icon = GIcons.Info, label = "Details", selected = false,
                            onClick = { showInfo = true },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        PillItem(
                            icon = GIcons.Settings, label = "Edit", selected = false,
                            onClick = { onEdit(current) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    PillItem(
                        icon = GIcons.Trash, label = "Delete", selected = false,
                        onClick = { vm.requestTrash(listOf(current)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (showInfo) {
        InfoSheet(item = current, onDismiss = { showInfo = false })
    }
}

@Composable
private fun VideoPage(
    item: MediaItem,
    insetTop: Dp,
    insetBottom: Dp,
    onTap: () -> Unit,
    onPlay: () -> Unit,
) {
    val ctx = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
            .padding(top = insetTop, bottom = insetBottom),
        contentAlignment = Alignment.Center,
    ) {
        val request = remember(item.uri) { ImageRequest.Builder(ctx).data(item.uri).size(1280).build() }
        // The frame hugs the video, so the duration badge sits on the video's own bottom-right corner.
        Box(
            modifier = Modifier
                .aspectRatio(
                    if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height else 16f / 9f,
                    matchHeightConstraintsFirst = false,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = request,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .bounceClick(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GIcons.Play, contentDescription = "Play video",
                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = formatDuration(item.durationMs),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(ChromeBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ZoomableImage(
    item: MediaItem,
    insetTop: Dp,
    insetBottom: Dp,
    onTap: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var imgSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // When turned sideways the picture must shrink/grow so it still fits between the bars.
    val fitFactor = run {
        val availW = box.width.toFloat()
        val availH = (box.height - with(density) { (insetTop + insetBottom).toPx() }).coerceAtLeast(1f)
        if (imgSize.width <= 0 || imgSize.height <= 0 || availW <= 0f) {
            1f
        } else {
            val iw = imgSize.width.toFloat()
            val ih = imgSize.height.toFloat()
            val s0 = minOf(availW / iw, availH / ih)
            val s1 = minOf(availW / ih, availH / iw)
            val t = kotlin.math.abs(kotlin.math.sin(Math.toRadians(rotation.toDouble()))).toFloat()
            1f + (s1 / s0 - 1f) * t
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            // swipe down = close (only while not zoomed)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (scale <= 1.01f) {
                            if (total > 200f) onSwipeDown()
                        }
                        total = 0f
                    },
                    onDragCancel = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        scope.launch {
                            val from = scale
                            val to = if (from > 1.05f) 1f else 3f
                            val fromOffset = offset
                            animate(
                                initialValue = from,
                                targetValue = to,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                            ) { v, _ ->
                                scale = v
                                offset = if (to == 1f) {
                                    val t = if (from > 1f) (v - 1f) / (from - 1f) else 0f
                                    Offset(fromOffset.x * t, fromOffset.y * t)
                                } else {
                                    Offset.Zero
                                }
                            }
                        }
                    },
                )
            }
            // pinch to zoom + pan while zoomed; at 1x we don't consume so the pager can swipe
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var twisted = false
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val multi = event.changes.count { it.pressed } >= 2
                        if (multi) {
                            twisted = true
                            rotation += event.calculateRotation()
                        }
                        val newScale = (scale * zoom).coerceIn(1f, 6f)
                        if (scale > 1f || newScale > 1f || multi) {
                            val maxX = box.width * (newScale - 1f) / 2f
                            val maxY = box.height * (newScale - 1f) / 2f
                            offset = if (newScale <= 1f) {
                                Offset.Zero
                            } else {
                                Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY),
                                )
                            }
                            scale = newScale
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (twisted) {
                        // Snap to the nearest quarter turn.
                        val from = rotation
                        val to = Math.round(from / 90f) * 90f
                        scope.launch {
                            animate(
                                initialValue = from,
                                targetValue = to,
                                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                            ) { v, _ -> rotation = v }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val request = remember(item.uri) { ImageRequest.Builder(ctx).data(item.uri).size(2048).build() }
        AsyncImage(
            model = request,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val d = state.result.drawable
                if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                    imgSize = IntSize(d.intrinsicWidth, d.intrinsicHeight)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insetTop, bottom = insetBottom)
                .graphicsLayer {
                    val sc = scale * fitFactor
                    scaleX = sc
                    scaleY = sc
                    rotationZ = rotation
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Details sheet
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(item: MediaItem, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val rows by produceState(initialValue = emptyList<Pair<String, String>>(), item.id) {
        value = readInfo(context, item)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        contentColor = cs.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(GIcons.Info, contentDescription = null, tint = cs.primary, modifier = Modifier.size(22.dp))
                Text("Details", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            rows.forEach { (label, value) ->
                Column {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
                    Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = cs.onBackground)
                }
            }
        }
    }
}

private fun openExif(context: Context, uri: android.net.Uri): ExifInterface? =
    context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }

private suspend fun readInfo(context: Context, item: MediaItem): List<Pair<String, String>> =
    withContext(Dispatchers.IO) {
        val rows = mutableListOf<Pair<String, String>>()
        rows += "Name" to item.name
        val dt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy  h:mm a", Locale.getDefault())
            .format(Instant.ofEpochMilli(item.dateMs).atZone(ZoneId.systemDefault()))
        rows += "Date" to dt
        rows += "Size" to formatSize(item.sizeBytes)
        if (item.width > 0 && item.height > 0) {
            val mp = item.width.toLong() * item.height / 1_000_000.0
            val res = "${item.width} x ${item.height}"
            rows += "Resolution" to if (!item.isVideo && mp >= 0.1) "$res  (%.1f MP)".format(mp) else res
        }
        if (item.isVideo && item.durationMs > 0) rows += "Duration" to formatDuration(item.durationMs)
        if (item.mime.isNotBlank()) rows += "Type" to item.mime
        rows += "Location" to (item.relativePath + item.name)

        if (!item.isVideo) {
            val exif = runCatching { openExif(context, MediaStore.setRequireOriginal(item.uri)) }.getOrNull()
                ?: runCatching { openExif(context, item.uri) }.getOrNull()
            if (exif != null) {
                val camera = listOfNotNull(
                    exif.getAttribute(ExifInterface.TAG_MAKE),
                    exif.getAttribute(ExifInterface.TAG_MODEL),
                ).joinToString(" ").trim()
                if (camera.isNotEmpty()) rows += "Camera" to camera
                val parts = mutableListOf<String>()
                exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { parts += "f/$it" }
                exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()?.let {
                    parts += if (it < 1.0 && it > 0.0) "1/${(1.0 / it).toInt()}s" else "${it}s"
                }
                exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { parts += "ISO $it" }
                exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { f ->
                    val v = f.split("/").let { p ->
                        if (p.size == 2) p[0].toDoubleOrNull()?.div(p[1].toDoubleOrNull() ?: 1.0) else f.toDoubleOrNull()
                    }
                    if (v != null) parts += "%.0f mm".format(v)
                }
                if (parts.isNotEmpty()) rows += "Exposure" to parts.joinToString("   ")
                val ll = exif.latLong
                if (ll != null) rows += "GPS" to "%.5f, %.5f".format(ll[0], ll[1])
            }
        }
        rows
    }
