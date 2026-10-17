@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.vibe.gallery.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.app.PictureInPictureParams
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import dev.vibe.gallery.data.MediaItem
import dev.vibe.gallery.data.MediaOps
import dev.vibe.gallery.data.PlayMode
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.vibe.gallery.data.formatDuration
import dev.vibe.gallery.ui.theme.GIcons
import dev.vibe.gallery.util.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.media3.common.MediaItem as ExoMediaItem

private val PlayerChrome = Color(0xB8141414)

/** side: 0 = top centre, -1 = left edge (brightness, rewind), 1 = right edge (volume, forward). */
private class Hud(val icon: ImageVector, val text: String, val fraction: Float? = null, val side: Int = 0)

private fun trackLabel(group: Tracks.Group, index: Int, prefix: String): String {
    val f = group.getTrackFormat(0)
    val lang = f.language
        ?.let { Locale.forLanguageTag(it).displayLanguage }
        ?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
    val codec = f.sampleMimeType?.substringAfter('/')?.uppercase(Locale.US)
    val label = f.label
    return listOfNotNull("$prefix ${index + 1}", label ?: lang, codec).joinToString(" · ")
}

@Composable
private fun PlayerChip(text: String, icon: ImageVector?, accent: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PlayerChrome)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text(
            text = text,
            color = if (accent) cs.primary else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RoundChrome(icon: ImageVector, desc: String, size: Int = 44, onClick: () -> Unit) {
    GIconButton(
        icon = icon, desc = desc, onClick = onClick,
        size = size.dp, container = PlayerChrome, tint = Color.White,
    )
}

@Composable
private fun SkipButton(icon: ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.bounceClick(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = desc,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(26.dp),
        )
    }
}

/** Set when one video hands over to another (Next / Previous / auto-advance) so the new one starts playing. */
private object PlayerHandoff {
    var autoStart = false
    var activeId = Long.MIN_VALUE
}

private fun subtitleConfig(uri: android.net.Uri, name: String): ExoMediaItem.SubtitleConfiguration {
    val lower = name.lowercase(Locale.US)
    val mime = when {
        lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        lower.endsWith(".ttml") || lower.endsWith(".xml") || lower.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }
    return ExoMediaItem.SubtitleConfiguration.Builder(uri)
        .setMimeType(mime)
        .setLanguage("und")
        .setLabel(name.substringBeforeLast('.').ifBlank { "Subtitles" })
        .build()
}

/** Subtitle files sitting next to the video with the same name (needs All files access). */
private fun findSiblingSubtitles(context: Context, item: MediaItem): List<ExoMediaItem.SubtitleConfiguration> {
    val path = MediaOps.pathOf(context, item.uri) ?: return emptyList()
    val base = path.substringBeforeLast('.')
    val exts = listOf("srt", "vtt", "ass", "ssa", "ttml")
    return exts.mapNotNull { ext ->
        val f = java.io.File("$base.$ext")
        if (f.isFile) subtitleConfig(android.net.Uri.fromFile(f), f.name) else null
    }
}

@Composable
fun PlayerScreen(vm: GalleryViewModel, itemId: Long, onBack: () -> Unit, onOpenVideo: (Long) -> Unit) {
    val item = remember(itemId) { vm.findItem(itemId) }
    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val context = LocalContext.current
    val activity = context.findActivity()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val config = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Subtitle files loaded by the user (or found next to the video), kept so adding one keeps the rest.
    var extSubs by remember {
        mutableStateOf(
            if (item.id >= 0 && MediaOps.hasAllFiles()) findSiblingSubtitles(context, item) else emptyList(),
        )
    }

    val player = remember {
        val factory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, factory).build().apply {
            setMediaItem(
                ExoMediaItem.Builder()
                    .setUri(item.uri)
                    .setSubtitleConfigurations(extSubs)
                    .build(),
            )
            playWhenReady = false
            prepare()
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Subtitles"
            val cfg = subtitleConfig(uri, name)
            val keep = extSubs.filter { it.uri != uri }
            extSubs = keep + cfg
            val pos = player.currentPosition
            val wasPlaying = player.playWhenReady
            player.setMediaItem(
                ExoMediaItem.Builder().setUri(item.uri).setSubtitleConfigurations(extSubs).build(),
                pos,
            )
            player.prepare()
            player.playWhenReady = wasPlaying
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        }
    }

    // ---- state ----------------------------------------------------------
    var isPlaying by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(item.durationMs) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var error by remember { mutableStateOf<String?>(null) }
    var controls by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var fill by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var boost by remember { mutableStateOf(false) }
    var inPip by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var speedMenu by remember { mutableStateOf(false) }
    var audioMenu by remember { mutableStateOf(false) }
    var subsMenu by remember { mutableStateOf(false) }
    var playlistMenu by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val menuOpen = speedMenu || audioMenu || subsMenu || playlistMenu
    var lastHud by remember { mutableStateOf<Hud?>(null) }
    if (hud != null) lastHud = hud

    // Previous / next video in the list the viewer was opened from.
    val viewerList by vm.viewerList.collectAsStateWithLifecycle()
    val videoList = remember(viewerList, itemId) { viewerList.filter { it.isVideo } }
    val videoIdx = videoList.indexOfFirst { it.id == itemId }
    val prevVideo = if (videoIdx > 0) videoList[videoIdx - 1] else null
    val nextVideo = if (videoIdx >= 0) videoList.getOrNull(videoIdx + 1) else null

    // The Next button / auto-advance: random pick in shuffle mode, otherwise the following video.
    fun pickNext(): MediaItem? = when {
        prefs.playMode == PlayMode.SHUFFLE && videoList.size > 1 ->
            videoList.filter { it.id != itemId }.randomOrNull()
        else -> nextVideo
    }

    fun openVideo(target: MediaItem) {
        PlayerHandoff.autoStart = true
        onOpenVideo(target.id)
    }

    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var brightness by remember {
        val cur = activity?.window?.attributes?.screenBrightness ?: -1f
        val value = if (cur >= 0f) {
            cur
        } else {
            runCatching {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
            }.getOrDefault(0.5f)
        }
        mutableFloatStateOf(value)
    }

    fun applyBrightness(v: Float) {
        brightness = v.coerceIn(0.02f, 1f)
        val window = activity?.window ?: return
        val lp = window.attributes
        lp.screenBrightness = brightness
        window.attributes = lp
    }

    fun seekBy(ms: Long) {
        val dur = if (player.duration > 0) player.duration else Long.MAX_VALUE
        player.seekTo((player.currentPosition + ms).coerceIn(0L, dur))
    }

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ratio = (item.width.toFloat() / item.height.coerceAtLeast(1).toFloat()).coerceIn(0.5f, 2.2f)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
                .build()
            runCatching { activity?.enterPictureInPictureMode(params) }
        }
    }

    // ---- player wiring ----------------------------------------------------
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && player.duration > 0) duration = player.duration
                ended = state == Player.STATE_ENDED
                if (ended) {
                    controls = true
                    if (prefs.playMode == PlayMode.NEXT || prefs.playMode == PlayMode.SHUFFLE) {
                        pickNext()?.let { openVideo(it) }
                    }
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                error = e.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (prefs.resume && item.id >= 0) {
            val saved = vm.resumePosition(item.id)
            if (saved != null && saved > 3000) player.seekTo(saved)
        }
        player.playWhenReady = prefs.autoplay || PlayerHandoff.autoStart
        PlayerHandoff.autoStart = false
    }

    // Loop mode repeats the current video; the other modes play it once.
    LaunchedEffect(prefs.playMode) {
        player.repeatMode = if (prefs.playMode == PlayMode.LOOP) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // Keep this video's position fresh so it survives the app being killed or switching videos.
    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            if (player.isPlaying && item.id >= 0) {
                vm.saveResume(item.id, player.currentPosition, player.duration.coerceAtLeast(0))
            }
        }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            delay(250)
        }
    }

    LaunchedEffect(controls, isPlaying, interaction, menuOpen, scrubbing) {
        if (controls && isPlaying && !menuOpen && !scrubbing) {
            delay(3500)
            controls = false
        }
    }

    LaunchedEffect(hud, boost) {
        if (hud != null && !boost) {
            delay(900)
            hud = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(activity) {
        val componentActivity = activity as? ComponentActivity
        val consumer = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
        }
        componentActivity?.addOnPictureInPictureModeChangedListener(consumer)
        onDispose { componentActivity?.removeOnPictureInPictureModeChangedListener(consumer) }
    }

    DisposableEffect(Unit) {
        PlayerHandoff.activeId = itemId
        val window = activity?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.requestedOrientation = if (item.width >= item.height) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        onDispose {
            // When switching videos the new screen is already set up; don't undo its settings.
            if (PlayerHandoff.activeId != itemId) return@onDispose
            ctrl?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                val lp = window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (item.id >= 0) vm.saveResume(item.id, player.currentPosition, player.duration.coerceAtLeast(0))
            player.release()
        }
    }

    // ---- UI -------------------------------------------------------------
    val exoPlayer = player
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = exoPlayer
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = {
                it.resizeMode = if (fill) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // gesture layer
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(locked, inPip) {
                    if (!locked && !inPip) {
                        detectTapGestures(
                            onPress = {
                                val job = scope.launch {
                                    delay(450)
                                    boost = true
                                    player.setPlaybackSpeed(2f)
                                    hud = Hud(GIcons.Play, "2x speed")
                                }
                                tryAwaitRelease()
                                job.cancel()
                                if (boost) {
                                    boost = false
                                    player.setPlaybackSpeed(speed)
                                }
                            },
                            onTap = {
                                controls = !controls
                                interaction++
                            },
                            onDoubleTap = { o ->
                                if (o.x < size.width / 2f) {
                                    seekBy(-10_000)
                                    hud = Hud(GIcons.FastRewind, "10s", null, -1)
                                } else {
                                    seekBy(10_000)
                                    hud = Hud(GIcons.FastForward, "10s", null, 1)
                                }
                            },
                        )
                    }
                }
                .pointerInput(locked, inPip) {
                    if (!locked && !inPip) {
                        var mode = 0
                        var startX = 0f
                        var startPos = 0L
                        var accum = 0f
                        var target = 0L
                        var volAccum = 0f
                        detectDragGestures(
                            onDragStart = { start ->
                                mode = 0
                                startX = start.x
                                startPos = player.currentPosition
                                target = startPos
                                accum = 0f
                                volAccum = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                            },
                            onDragEnd = {
                                if (mode == 1) player.seekTo(target)
                                mode = 0
                            },
                            onDragCancel = { mode = 0 },
                            onDrag = { change, drag ->
                                change.consume()
                                if (mode == 0) {
                                    mode = if (abs(drag.x) > abs(drag.y)) {
                                        1
                                    } else if (startX < size.width / 2f) {
                                        2
                                    } else {
                                        3
                                    }
                                }
                                when (mode) {
                                    1 -> {
                                        accum += drag.x
                                        val dur = if (player.duration > 0) player.duration else Long.MAX_VALUE
                                        target = (startPos + accum / size.width * 90_000f).toLong().coerceIn(0L, dur)
                                        val delta = (target - startPos) / 1000
                                        hud = Hud(
                                            GIcons.Play,
                                            (if (delta >= 0) "+" else "") + delta + "s  ·  " + formatDuration(target),
                                        )
                                    }
                                    2 -> {
                                        applyBrightness(brightness - drag.y / size.height * 1.2f)
                                        hud = Hud(GIcons.Brightness, "${(brightness * 100).roundToInt()}%", brightness, -1)
                                    }
                                    3 -> {
                                        volAccum = (volAccum - drag.y / size.height * maxVol * 1.2f)
                                            .coerceIn(0f, maxVol.toFloat())
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volAccum.roundToInt(), 0)
                                        hud = Hud(GIcons.Volume, "${(volAccum / maxVol * 100).roundToInt()}%", volAccum / maxVol, 1)
                                    }
                                    else -> {}
                                }
                            },
                        )
                    }
                }
        )

        // gesture HUD
        AnimatedVisibility(
            visible = hud != null && hud?.side == 0,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .displayCutoutPadding()
                .padding(top = 28.dp),
            enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.85f),
            exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.9f),
        ) {
            val h = lastHud
            if (h != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(PlayerChrome)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(h.icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
                    Text(h.text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    if (h.fraction != null) {
                        Box(
                            Modifier
                                .width(70.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(h.fraction.coerceIn(0f, 1f))
                                    .background(cs.primary)
                            )
                        }
                    }
                }
            }
        }

        // side HUDs: rewind / brightness on the left, forward / volume on the right
        for (side in listOf(-1, 1)) {
            AnimatedVisibility(
                visible = hud != null && hud?.side == side,
                modifier = Modifier
                    .align(if (side < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .displayCutoutPadding()
                    .padding(horizontal = 28.dp),
                enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.85f),
                exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.9f),
            ) {
                val h = lastHud
                if (h != null && h.side == side && h.fraction == null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(PlayerChrome)
                            .width(92.dp)
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(h.icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(h.text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                    }
                } else if (h != null && h.side == side) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(PlayerChrome)
                            .width(if (h.fraction != null) 58.dp else 72.dp)
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(h.icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(22.dp))
                        if (h.fraction != null) {
                            Box(
                                Modifier
                                    .width(5.dp)
                                    .height(88.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(h.fraction.coerceIn(0f, 1f))
                                        .background(cs.primary)
                                )
                            }
                        }
                        Text(
                            h.text,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.width(44.dp),
                        )
                    }
                }
            }
        }

        // error
        if (error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Can't play this video", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(error ?: "", color = Color(0xFF8A8A8E), fontSize = 12.sp)
            }
        }

        // lock indicator
        AnimatedVisibility(
            visible = locked && !inPip,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .displayCutoutPadding()
                .padding(start = 24.dp),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            RoundChrome(GIcons.Lock, "Unlock controls", 52) {
                locked = false
                controls = true
            }
        }

        // controls
        val showControls = controls && !locked && !inPip
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160)),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color(0xC7000000), Color.Transparent)))
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD1000000))))
                )

                // top bar
                AnimatedVisibility(
                    visible = showControls,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = slideInVertically(tween(220)) { -it / 2 },
                    exit = slideOutVertically(tween(180)) { -it / 2 },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .displayCutoutPadding()
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RoundChrome(GIcons.Back, "Back") { onBack() }
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            val info = buildString {
                                append(item.mime.substringAfter('/', "").uppercase(Locale.US))
                                if (item.width > 0) append("  ·  ${item.width} x ${item.height}")
                            }
                            Text(info, color = Color(0xFFA8A8AC), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        RoundChrome(GIcons.Pip, "Picture in picture") { enterPip() }
                        Box {
                            RoundChrome(GIcons.Subtitles, "Subtitles") { subsMenu = true }
                            DropdownMenu(expanded = subsMenu, onDismissRequest = { subsMenu = false }) {
                                val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                                CompactMenuItem(
                                    text = { Text("Off") },
                                    onClick = {
                                        subsMenu = false
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                                    },
                                )
                                textGroups.forEachIndexed { i, g ->
                                    CompactMenuItem(
                                        text = { Text(trackLabel(g, i, "Subtitle")) },
                                        onClick = {
                                            subsMenu = false
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
                                                .build()
                                        },
                                    )
                                }
                                if (textGroups.isEmpty()) {
                                    CompactMenuItem(text = { Text("No subtitles in this file") }, enabled = false, onClick = {})
                                }
                                CompactMenuItem(
                                    text = { Text("Load subtitle file…") },
                                    onClick = {
                                        subsMenu = false
                                        subtitlePicker.launch(arrayOf("*/*"))
                                    },
                                )
                            }
                        }
                    }
                }

                // center controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                ) {
                    SkipButton(GIcons.SkipPrevious, "Previous video", prevVideo != null) {
                        prevVideo?.let { openVideo(it) }
                    }
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(cs.primary)
                            .bounceClick(onClick = { togglePlay(); interaction++ }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Crossfade(targetState = isPlaying, animationSpec = tween(160), label = "playPause") { playing ->
                            Icon(
                                imageVector = if (playing) GIcons.Pause else GIcons.Play,
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = cs.onPrimary,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }

                    SkipButton(GIcons.SkipNext, "Next video", pickNext() != null) {
                        pickNext()?.let { openVideo(it) }
                    }
                }

                // bottom bar
                AnimatedVisibility(
                    visible = showControls,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(tween(240)) { it / 2 },
                    exit = slideOutVertically(tween(180)) { it / 2 },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .displayCutoutPadding()
                            .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                formatDuration(if (scrubbing) (scrubValue * duration).toLong() else position),
                                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(44.dp),
                            )
                            val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                            Slider(
                                value = if (scrubbing) scrubValue else fraction,
                                onValueChange = {
                                    scrubbing = true
                                    scrubValue = it
                                },
                                onValueChangeFinished = {
                                    val target = (scrubValue * duration).toLong()
                                    player.seekTo(target)
                                    position = target
                                    scrubbing = false
                                    interaction++
                                },
                                thumb = {
                                    Box(
                                        Modifier
                                            .size(width = 4.dp, height = 22.dp)
                                            .clip(CircleShape)
                                            .background(cs.primary)
                                    )
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = cs.primary,
                                    activeTrackColor = cs.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatDuration(duration),
                                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(44.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PlayerChip("Lock", GIcons.Lock) {
                                    locked = true
                                    controls = false
                                }
                                Box {
                                    PlayerChip("${if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()}x", null, accent = true) {
                                        speedMenu = true
                                    }
                                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                                            CompactMenuItem(
                                                text = { Text("${s}x") },
                                                onClick = {
                                                    speedMenu = false
                                                    speed = s
                                                    player.setPlaybackSpeed(s)
                                                },
                                            )
                                        }
                                    }
                                }
                                Box(Modifier.weight(1f, fill = false)) {
                                    val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                                    val selectedIdx = audioGroups.indexOfFirst { it.isSelected }
                                    PlayerChip(
                                        text = if (selectedIdx >= 0) trackLabel(audioGroups[selectedIdx], selectedIdx, "Audio") else "Audio",
                                        icon = GIcons.Audio,
                                    ) { audioMenu = true }
                                    DropdownMenu(expanded = audioMenu, onDismissRequest = { audioMenu = false }) {
                                        audioGroups.forEachIndexed { i, g ->
                                            CompactMenuItem(
                                                text = { Text(trackLabel(g, i, "Audio")) },
                                                onClick = {
                                                    audioMenu = false
                                                    player.trackSelectionParameters = player.trackSelectionParameters
                                                        .buildUpon()
                                                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
                                                        .build()
                                                },
                                            )
                                        }
                                        if (audioGroups.isEmpty()) {
                                            CompactMenuItem(text = { Text("No audio tracks") }, enabled = false, onClick = {})
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val landNow = config.orientation == Configuration.ORIENTATION_LANDSCAPE
                                Box {
                                    RoundChrome(
                                        when (prefs.playMode) {
                                            PlayMode.LOOP -> GIcons.Repeat
                                            PlayMode.SHUFFLE -> GIcons.Shuffle
                                            else -> GIcons.Playlist
                                        },
                                        "Playback mode",
                                        36,
                                    ) { playlistMenu = true }
                                    DropdownMenu(expanded = playlistMenu, onDismissRequest = { playlistMenu = false }) {
                                        listOf(
                                            PlayMode.ONCE to "Play once",
                                            PlayMode.NEXT to "Play next video",
                                            PlayMode.LOOP to "Loop this video",
                                            PlayMode.SHUFFLE to "Shuffle",
                                        ).forEach { (mode, label) ->
                                            CompactMenuItem(
                                                text = { Text(if (prefs.playMode == mode) "✓  $label" else "     $label") },
                                                onClick = {
                                                    playlistMenu = false
                                                    vm.setPlayMode(mode)
                                                },
                                            )
                                        }
                                    }
                                }
                                RoundChrome(GIcons.ScreenRotate, "Rotate screen", 36) {
                                    val land = landNow
                                    activity?.requestedOrientation = if (land) {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                }
                                RoundChrome(GIcons.Fit, "Fit or fill screen", 36) { fill = !fill }
                            }
                        }
                    }
                }
            }
        }
    }
}
