package dev.vibe.gallery

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.vibe.gallery.data.MediaItem
import dev.vibe.gallery.data.Prefs
import dev.vibe.gallery.ui.ZoomableImage
import dev.vibe.gallery.ui.theme.GalleryTheme

/** Lets other apps open images and videos in this gallery ("Open with"). */
class ExternalViewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent?.data
            ?: if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri == null) {
            finish()
            return
        }
        val mime = intent?.type ?: contentResolver.getType(uri) ?: ""
        val video = mime.startsWith("video/")
        val item = MediaItem(
            id = -1L, uri = uri, name = uri.lastPathSegment ?: "", mime = mime, isVideo = video,
            dateMs = 0L, width = 0, height = 0, durationMs = 0L, sizeBytes = 0L,
            bucketId = 0L, bucketName = "", relativePath = "",
        )
        setContent {
            GalleryTheme(Prefs()) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (video) VideoView(uri) else ZoomableImage(
                        item = item,
                        insetTop = 0.dp,
                        insetBottom = 0.dp,
                        onTap = {},
                        onSwipeDown = { finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoView(uri: Uri) {
    val ctx = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(ExoItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView(
        factory = { c -> PlayerView(c).apply { this.player = player; useController = true } },
        modifier = Modifier.fillMaxSize(),
    )
}
