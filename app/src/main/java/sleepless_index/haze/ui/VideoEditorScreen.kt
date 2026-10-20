@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package sleepless_index.haze.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
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
private val HandleW = 22.dp
private val StripH = 56.dp

private enum class Tool(val label: String) { Trim("Trim"), Crop("Crop"), Adjust("Adjust"), Filters("Filters") }

private data class Look(
    val name: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val warmth: Float,
    val gray: Boolean,
)

private val Looks = listOf(
    Look("None", 0f, 0f, 0f, 0f, false),
    Look("B&W", 0f, 0.1f, 0f, 0f, true),
    Look("Noir", -0.05f, 0.4f, 0f, 0f, true),
    Look("Warm", 0.02f, 0.05f, 12f, 0.6f, false),
    Look("Cool", 0f, 0.05f, 5f, -0.6f, false),
    Look("Vivid", 0.02f, 0.2f, 45f, 0f, false),
    Look("Fade", 0.1f, -0.25f, -25f, 0.1f, false),
)

private data class AspectOpt(val label: String, val ratio: Float?)

private val Aspects = listOf(
    AspectOpt("Original", null),
    AspectOpt("1:1", 1f),
    AspectOpt("4:5", 0.8f),
    AspectOpt("3:4", 0.75f),
    AspectOpt("9:16", 9f / 16f),
    AspectOpt("16:9", 16f / 9f),
    AspectOpt("21:9", 21f / 9f),
)

/** Turns the editor's choices into Media3 effects (used for the live preview and the export). */
private fun buildEffects(
    quarter: Int,
    flip: Boolean,
    aspect: Float?,
    srcW: Int,
    srcH: Int,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    warmth: Float,
    gray: Boolean,
): List<Effect> {
    val out = ArrayList<Effect>()
    if (quarter % 4 != 0) {
        out.add(ScaleAndRotateTransformation.Builder().setRotationDegrees((quarter % 4) * 90f).build())
    }
    if (flip) {
        out.add(ScaleAndRotateTransformation.Builder().setScale(-1f, 1f).build())
    }
    if (aspect != null) {
        val odd = quarter % 2 != 0
        val fw = (if (odd) srcH else srcW).coerceAtLeast(1)
        val fh = (if (odd) srcW else srcH).coerceAtLeast(1)
        val frame = fw.toFloat() / fh
        val halfW: Float
        val halfH: Float
        if (aspect > frame) {
            halfW = 1f
            halfH = (frame / aspect).coerceIn(0.05f, 1f)
        } else {
            halfH = 1f
            halfW = (aspect / frame).coerceIn(0.05f, 1f)
        }
        if (halfW < 0.999f || halfH < 0.999f) out.add(Crop(-halfW, halfW, -halfH, halfH))
    }
    if (brightness != 0f) out.add(Brightness(brightness.coerceIn(-1f, 1f)))
    if (contrast != 0f) out.add(Contrast(contrast.coerceIn(-1f, 1f)))
    if (saturation != 0f) out.add(HslAdjustment.Builder().adjustSaturation(saturation.coerceIn(-100f, 100f)).build())
    if (warmth != 0f) {
        out.add(
            RgbAdjustment.Builder()
                .setRedScale(1f + warmth * 0.25f)
                .setBlueScale(1f - warmth * 0.25f)
                .build()
        )
    }
    if (gray) out.add(RgbFilter.createGrayscaleFilter())
    return out
}

/** Video editor: trim, crop, rotate, flip, adjust and filters. Saves the result as a new video. */
@Composable
fun VideoEditorScreen(vm: GalleryViewModel, itemId: Long, onClose: () -> Unit) {
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

    var tool by remember { mutableStateOf(Tool.Trim) }
    var durationMs by remember { mutableLongStateOf(item.durationMs.coerceAtLeast(1L)) }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(item.durationMs.coerceAtLeast(1L)) }
    var playhead by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var mute by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var transformer by remember { mutableStateOf<Transformer?>(null) }

    var quarter by remember { mutableIntStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    var aspectIdx by remember { mutableIntStateOf(0) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var warmth by remember { mutableFloatStateOf(0f) }
    var gray by remember { mutableStateOf(false) }
    var lookIdx by remember { mutableIntStateOf(0) }

    val effects = remember(quarter, flip, aspectIdx, brightness, contrast, saturation, warmth, gray) {
        buildEffects(
            quarter, flip, Aspects[aspectIdx].ratio, item.width, item.height,
            brightness, contrast, saturation, warmth, gray,
        )
    }
    LaunchedEffect(effects) { player.setVideoEffects(effects) }

    // Keeps the playhead in sync, picks up the real duration, and stops at the end of the range.
    LaunchedEffect(player) {
        while (true) {
            val pos = player.currentPosition
            playhead = pos
            playing = player.isPlaying
            val d = player.duration
            if (d > 0 && d != durationMs) {
                val wasFull = endMs >= durationMs
                durationMs = d
                if (wasFull || endMs > d) endMs = d
            }
            if (player.isPlaying && endMs > startMs && pos >= endMs) {
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
            if (player.currentPosition < startMs || player.currentPosition >= endMs - 100L) player.seekTo(startMs)
            player.playWhenReady = true
            player.play()
        }
    }

    fun applyLook(i: Int) {
        lookIdx = i
        val l = Looks[i]
        brightness = l.brightness
        contrast = l.contrast
        saturation = l.saturation
        warmth = l.warmth
        gray = l.gray
    }

    fun save() {
        if (exporting) return
        player.pause()
        exporting = true
        progress = 0f
        scope.launch {
            val out = File(context.cacheDir, "edit_${System.currentTimeMillis()}.mp4")
            val ok = runCatching {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val clip = ExoMediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                    val media = ExoMediaItem.Builder().setUri(item.uri).setClippingConfiguration(clip).build()
                    val edited = EditedMediaItem.Builder(media)
                        .setRemoveAudio(mute)
                        .setEffects(Effects(emptyList(), effects))
                        .build()
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
                                name = "${base}_edit.mp4",
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
                vm.message("Couldn't save this video")
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
            GIconButton(GIcons.Close, "Close editor", onClick = { if (!exporting) onClose() })
            Text(
                "Edit video", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                modifier = Modifier.weight(1f).padding(start = 14.dp),
            )
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(cs.primary)
                    .bounceClick(onClick = { save() })
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Save copy", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = cs.onPrimary)
            }
        }

        // Preview (shows the effects live)
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
            // Tap the preview to play or pause
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { togglePlay() } }
            )
        }

        Spacer(Modifier.height(10.dp))

        // Tool panel
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            when (tool) {
                Tool.Trim -> Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(formatTime(startMs), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("${formatTime(endMs - startMs)} selected", fontSize = 13.sp, color = cs.onSurfaceVariant)
                        Text(formatTime(endMs), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
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
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GIconButton(
                            if (playing) GIcons.Pause else GIcons.Play,
                            if (playing) "Pause preview" else "Play preview",
                            onClick = { togglePlay() },
                        )
                        EditChip(if (mute) "Audio off" else "Audio on", mute) { mute = !mute }
                    }
                }

                Tool.Crop -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Aspects.forEachIndexed { i, a -> EditChip(a.label, aspectIdx == i) { aspectIdx = i } }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EditChip("Rotate left", false) { quarter = (quarter + 1) % 4 }
                        EditChip("Rotate right", false) { quarter = (quarter + 3) % 4 }
                        EditChip("Flip", flip) { flip = !flip }
                    }
                    Text(
                        "Crop keeps the centre of the frame.",
                        fontSize = 12.sp, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                Tool.Adjust -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                ) {
                    AdjustRow("Brightness", brightness, -0.5f..0.5f) { brightness = it; lookIdx = -1 }
                    AdjustRow("Contrast", contrast, -0.5f..0.5f) { contrast = it; lookIdx = -1 }
                    AdjustRow("Saturation", saturation, -100f..100f) { saturation = it; lookIdx = -1 }
                    AdjustRow("Warmth", warmth, -1f..1f) { warmth = it; lookIdx = -1 }
                }

                Tool.Filters -> Row(
                    modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Looks.forEachIndexed { i, l -> EditChip(l.name, lookIdx == i) { applyLook(i) } }
                }
            }
        }

        // Tool tabs
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tool.values().forEach { t ->
                val selected = tool == t
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) cs.primary.copy(alpha = 0.2f) else Color.Transparent)
                        .bounceClick(onClick = { tool = t })
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        t.label, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (selected) cs.primary else cs.onSurfaceVariant,
                    )
                }
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
                Text("Saving…", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.width(220.dp),
                    color = cs.primary,
                    trackColor = cs.surfaceVariant,
                )
                Text("${(progress * 100).roundToInt()}%", fontSize = 13.sp, color = cs.onSurfaceVariant)
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

@Composable
private fun EditChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) cs.primary.copy(alpha = 0.22f) else cs.surfaceVariant)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) cs.primary else cs.onBackground,
        )
    }
}

@Composable
private fun AdjustRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.width(84.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = cs.primary,
                activeTrackColor = cs.primary,
                inactiveTrackColor = cs.surfaceVariant,
            ),
        )
    }
}

/** Frame strip with two draggable handles and a playhead. */
@Composable
internal fun TrimStrip(
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
    var outerPx by remember { mutableFloatStateOf(1f) }
    val handlePx = with(density) { HandleW.toPx() }
    val trackPx = (outerPx - 2f * handlePx).coerceAtLeast(1f)

    val dur by rememberUpdatedState(durationMs)
    val start by rememberUpdatedState(startMs)
    val end by rememberUpdatedState(endMs)
    val w by rememberUpdatedState(trackPx)

    fun xOf(ms: Long): Float = (ms.toFloat() / durationMs.coerceAtLeast(1L)) * trackPx

    // The outer box is wide enough to contain both handles, so they are always touchable.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(StripH)
            .onSizeChanged { outerPx = it.width.toFloat().coerceAtLeast(1f) },
    ) {
        // Track: frames, dimming, selection frame and playhead
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = HandleW),
        ) {
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

            // Tap or drag on the frames to move the preview
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
                    .width(with(density) { xOf(startMs).toDp() })
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
            Box(
                Modifier
                    .offset { IntOffset(xOf(endMs).roundToInt(), 0) }
                    .width(with(density) { (trackPx - xOf(endMs)).coerceAtLeast(0f).toDp() })
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
        }

        // Handles: the start handle sits left of the selection, the end handle right of it.
        Handle(
            modifier = Modifier
                .offset { IntOffset(xOf(startMs).roundToInt(), 0) }
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
                .offset { IntOffset((handlePx + xOf(endMs)).roundToInt(), 0) }
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
