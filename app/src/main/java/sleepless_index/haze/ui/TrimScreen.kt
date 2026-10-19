@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package sleepless_index.haze.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import sleepless_index.haze.data.MediaOps
import sleepless_index.haze.data.formatDuration
import sleepless_index.haze.ui.theme.GIcons
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private const val MIN_CLIP_MS = 500L
private val HandleW = 14.dp
private val StripH = 56.dp

/** Video trimmer: pick a start and end, preview the range, save the cut as a new video. */
@Composable
fun TrimScreen(vm: GalleryViewModel, itemId: Long, onClose: () -> Unit) {
    val item = remember(itemId) { vm.findItem(itemId) }
    if (item == null) {
        LaunchedEffect(Unit) { onClose() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(item.uri))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var durationMs by remember { mutableLongStateOf(item.durationMs.coerceAtLeast(1L)) }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(item.durationMs.coerceAtLeast(1L)) }
    var playhead by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var mute by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var transformer by remember { mutableStateOf<Transformer?>(null) }

    // The real duration is known once the player has read the file.
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && player.duration > 0 && player.duration != durationMs) {
                    val wasFull = endMs >= durationMs
                    durationMs = player.duration
                    if (wasFull) endMs = durationMs
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    // Keeps the playhead in sync and stops at the end of the selected range.
    LaunchedEffect(player) {
        while (true) {
            val pos = player.currentPosition
            playhead = pos
            if (player.isPlaying && pos >= endMs) {
                player.pause()
                player.seekTo(startMs)
                playhead = startMs
            }
            delay(40)
        }
    }

    fun seekPreview(ms: Long) {
        player.pause()
        player.seekTo(ms.coerceIn(0L, durationMs))
        playhead = ms
    }

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.currentPosition !in startMs until endMs) player.seekTo(startMs)
            player.play()
        }
    }

    fun save() {
        if (exporting) return
        player.pause()
        exporting = true
        progress = 0f
        scope.launch {
            val out = File(context.cacheDir, "trim_${System.currentTimeMillis()}.mp4")
            val ok = runCatching {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val clip = ExoMediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                    val media = ExoMediaItem.Builder().setUri(item.uri).setClippingConfiguration(clip).build()
                    val edited = EditedMediaItem.Builder(media).setRemoveAudio(mute).build()
                    val t = Transformer.Builder(context)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (cont.isActive) cont.resume(true)
                            }
                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                if (cont.isActive) cont.resume(false)
                            }
                        })
                        .build()
                    transformer = t
                    cont.invokeOnCancellation { t.cancel() }
                    t.start(edited, out.absolutePath)
                    // Poll progress while the export runs.
                    scope.launch {
                        val holder = ProgressHolder()
                        while (cont.isActive) {
                            if (t.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED) {
                                progress = holder.progress / 100f
                            }
                            delay(150)
                        }
                    }
                }
            }.getOrDefault(false)
            transformer = null

            val saved = if (ok && out.exists() && out.length() > 0) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val base = item.name.substringBeforeLast('.', item.name)
                        FileInputStream(out).use { input ->
                            MediaOps.insert(
                                context = context,
                                input = input,
                                name = "${base}_trim.mp4",
                                mime = "video/mp4",
                                isVideo = true,
                                dateMs = item.dateMs,
                                relativeDir = "Movies/Edited",
                            )
                        }
                    }.getOrNull()
                }
            } else null
            runCatching { out.delete() }
            exporting = false
            if (saved != null) {
                vm.message("Saved a copy to Movies/Edited")
                vm.refresh()
                onClose()
            } else {
                vm.message("Couldn't trim this video")
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GIconButton(GIcons.Close, "Close trimmer", onClick = { if (!exporting) onClose() })
            Text(
                "Trim", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                modifier = Modifier.weight(1f).padding(start = 14.dp),
            )
        }

        // Preview
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val exo = player
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = exo
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        // Times
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatTime(startMs), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "${formatTime(endMs - startMs)} selected",
                fontSize = 13.sp, color = cs.onSurfaceVariant,
            )
            Text(formatTime(endMs), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(Modifier.height(8.dp))

        TrimStrip(
            item = item,
            durationMs = durationMs,
            startMs = startMs,
            endMs = endMs,
            playheadMs = playhead,
            accent = cs.primary,
            onStart = { startMs = it; seekPreview(it) },
            onEnd = { endMs = it; seekPreview((it - 300L).coerceAtLeast(startMs)) },
            onScrub = { seekPreview(it.coerceIn(startMs, endMs)) },
        )

        Spacer(Modifier.height(16.dp))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GIconButton(
                if (playing) GIcons.Pause else GIcons.Play,
                if (playing) "Pause preview" else "Play preview",
                onClick = { togglePlay() },
            )
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (mute) cs.primary.copy(alpha = 0.22f) else cs.surfaceVariant)
                    .bounceClick(onClick = { mute = !mute })
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (mute) "Audio off" else "Audio on",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (mute) cs.primary else cs.onBackground,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(cs.primary)
                    .bounceClick(onClick = { save() })
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Save copy", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = cs.onPrimary)
            }
        }
    }

    if (exporting) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF161616))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Trimming…", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.width(220.dp),
                    color = cs.primary,
                    trackColor = cs.surfaceVariant,
                )
                Text(
                    "${(progress * 100).roundToInt()}%",
                    fontSize = 13.sp, color = cs.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(cs.surfaceVariant)
                        .bounceClick(onClick = { transformer?.cancel() })
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
                }
            }
        }
    }
}

/** Frame strip with two draggable handles and a playhead. */
@Composable
private fun TrimStrip(
    item: sleepless_index.haze.data.MediaItem,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    playheadMs: Long,
    accent: Color,
    onStart: (Long) -> Unit,
    onEnd: (Long) -> Unit,
    onScrub: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var widthPx by remember { mutableFloatStateOf(1f) }
    val handlePx = with(density) { HandleW.toPx() }

    val dur by rememberUpdatedState(durationMs)
    val start by rememberUpdatedState(startMs)
    val end by rememberUpdatedState(endMs)
    val w by rememberUpdatedState(widthPx)

    fun xOf(ms: Long): Float = (ms.toFloat() / durationMs.coerceAtLeast(1L)) * widthPx

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HandleW)
            .height(StripH)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) },
    ) {
        // Frames
        Row(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))) {
            val count = 8
            for (i in 0 until count) {
                val at = (dur * (i + 0.5f) / count).toLong()
                val req = remember(item.uri, at) {
                    ImageRequest.Builder(ctx)
                        .data(item.uri)
                        .videoFrameMillis(at)
                        .size(160)
                        .build()
                }
                AsyncImage(
                    model = req,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF1A1A1A)),
                )
            }
        }

        // Tap or drag on the strip to move the preview
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { o -> onScrub(((o.x / w) * dur).toLong()) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onScrub(((change.position.x / w) * dur).toLong())
                    }
                }
        )

        // Dim everything outside the selection
        Box(
            Modifier
                .offset { IntOffset(0, 0) }
                .width(with(density) { xOf(startMs).toDp() })
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.6f))
        )
        Box(
            Modifier
                .offset { IntOffset(xOf(endMs).roundToInt(), 0) }
                .width(with(density) { (widthPx - xOf(endMs)).coerceAtLeast(0f).toDp() })
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.6f))
        )

        // Selection frame
        Box(
            Modifier
                .offset { IntOffset(xOf(startMs).roundToInt(), 0) }
                .width(with(density) { (xOf(endMs) - xOf(startMs)).coerceAtLeast(0f).toDp() })
                .fillMaxHeight()
                .border(2.dp, accent, RoundedCornerShape(4.dp))
        )

        // Playhead
        if (playheadMs in startMs..endMs) {
            Box(
                Modifier
                    .offset { IntOffset(xOf(playheadMs).roundToInt() - 1.dp.roundToPx(), 0) }
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color.White)
            )
        }

        // Handles (sit just outside the selection so they never cover the frames inside it)
        Handle(
            modifier = Modifier
                .offset { IntOffset(xOf(startMs).roundToInt() - handlePx.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        val next = (start + (dx / w * dur).toLong()).coerceIn(0L, end - MIN_CLIP_MS)
                        onStart(next)
                    }
                },
            accent = accent,
        )
        Handle(
            modifier = Modifier
                .offset { IntOffset(xOf(endMs).roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        val next = (end + (dx / w * dur).toLong()).coerceIn(start + MIN_CLIP_MS, dur)
                        onEnd(next)
                    }
                },
            accent = accent,
        )
    }
}

@Composable
private fun Handle(modifier: Modifier, accent: Color) {
    Box(
        modifier = modifier
            .width(HandleW)
            .fillMaxHeight()
            .clip(RoundedCornerShape(5.dp))
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Black.copy(alpha = 0.45f))
        )
    }
}

private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val tenths = (total % 1000) / 100
    return "${formatDuration(total)}.$tenths"
}
