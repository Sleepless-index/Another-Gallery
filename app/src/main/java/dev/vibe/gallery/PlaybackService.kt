package dev.vibe.gallery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper

/** The player (and its media session) the service controls; set by the player screen while it is alive. */
object PlaybackBridge {
    var player: Player? = null
    var session: MediaSession? = null
}

/**
 * Keeps playback alive in the background and shows a media notification with
 * previous / play-pause / next / close, the video's thumbnail and its title.
 * The player itself lives in the player screen; this service only controls it.
 */
class PlaybackService : Service() {

    private var attached: Player? = null
    private var thumbUri: Uri? = null
    private var thumb: Bitmap? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(null)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val p = PlaybackBridge.player
        when (intent?.action) {
            ACTION_TOGGLE -> p?.let { if (it.isPlaying) it.pause() else it.play() }
            ACTION_NEXT -> p?.seekToNextMediaItem()
            ACTION_PREV -> p?.seekToPreviousMediaItem()
            ACTION_CLOSE -> {
                p?.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (p != null && attached !== p) {
            attached?.removeListener(listener)
            p.addListener(listener)
            attached = p
        }
        refresh(intent?.getStringExtra(EXTRA_TITLE))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        attached?.removeListener(listener)
        attached = null
        super.onDestroy()
    }

    private fun action(code: Int, act: String): PendingIntent =
        PendingIntent.getService(
            this,
            code,
            Intent(this, PlaybackService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun thumbnailFor(uri: Uri?): Bitmap? {
        if (uri == null) return null
        if (uri == thumbUri) return thumb
        val bmp = runCatching {
            if (Build.VERSION.SDK_INT >= 29) contentResolver.loadThumbnail(uri, Size(384, 384), null) else null
        }.getOrNull()
        thumbUri = uri
        thumb = bmp
        return bmp
    }

    private fun refresh(fallbackTitle: String?) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Background play", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val p = PlaybackBridge.player
        val item = p?.currentMediaItem
        val title = item?.mediaMetadata?.title?.toString() ?: fallbackTitle ?: "Playing"
        val playing = p?.isPlaying == true
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(if (playing) R.drawable.ic_notif_play else R.drawable.ic_notif_pause)
            .setContentTitle(title)
            .setContentText(if (playing) "Playing" else "Paused")
            .setSubText("Haze")
            .setLargeIcon(thumbnailFor(item?.localConfiguration?.uri))
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_notif_prev, "Previous", action(1, ACTION_PREV))
            .addAction(
                if (playing) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
                if (playing) "Pause" else "Play",
                action(2, ACTION_TOGGLE),
            )
            .addAction(R.drawable.ic_notif_next, "Next", action(3, ACTION_NEXT))
            .addAction(R.drawable.ic_notif_close, "Close", action(4, ACTION_CLOSE))
        PlaybackBridge.session?.let {
            b.setStyle(MediaStyleNotificationHelper.MediaStyle(it).setShowActionsInCompactView(0, 1, 2))
        }
        val n: Notification = b.build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        runCatching { ServiceCompat.startForeground(this, 41, n, type) }
    }

    companion object {
        private const val CHANNEL = "background_play"
        private const val EXTRA_TITLE = "title"
        private const val ACTION_TOGGLE = "dev.vibe.gallery.TOGGLE"
        private const val ACTION_NEXT = "dev.vibe.gallery.NEXT"
        private const val ACTION_PREV = "dev.vibe.gallery.PREV"
        private const val ACTION_CLOSE = "dev.vibe.gallery.CLOSE"

        fun start(context: Context, title: String) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PlaybackService::class.java).putExtra(EXTRA_TITLE, title),
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
        }
    }
}
