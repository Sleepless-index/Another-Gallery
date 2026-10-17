@file:OptIn(ExperimentalMaterial3Api::class)

package dev.vibe.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import dev.vibe.gallery.data.LoopMode
import dev.vibe.gallery.data.MediaItem
import dev.vibe.gallery.data.formatDuration
import dev.vibe.gallery.data.thumbRequest
import dev.vibe.gallery.ui.theme.GIcons

private val Panel = Color(0xFF121212)

@Composable
private fun MenuTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tint = if (active) cs.primary else Color.White
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .bounceClick(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        Text(
            label, color = tint, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/** Grid of player options, opened from the menu button. */
@Composable
fun PlayerMenuDialog(
    bgPlay: Boolean,
    loop: LoopMode,
    shuffle: Boolean,
    autoNext: Boolean,
    timerMin: Int,
    mirror: Boolean,
    onBgPlay: () -> Unit,
    onQueue: () -> Unit,
    onLoop: () -> Unit,
    onShuffle: () -> Unit,
    onAutoNext: () -> Unit,
    onTimer: () -> Unit,
    onMirror: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Panel)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                MenuTile(GIcons.Headphones, "Background play", bgPlay, Modifier.weight(1f), onBgPlay)
                MenuTile(GIcons.Playlist, "Queue", false, Modifier.weight(1f)) { onDismiss(); onQueue() }
                MenuTile(
                    GIcons.Repeat,
                    when (loop) {
                        LoopMode.OFF -> "Loop off"
                        LoopMode.ONE -> "Loop one"
                        LoopMode.ALL -> "Loop all"
                    },
                    loop != LoopMode.OFF,
                    Modifier.weight(1f),
                    onLoop,
                )
                MenuTile(GIcons.Shuffle, "Shuffle", shuffle, Modifier.weight(1f), onShuffle)
            }
            Row(Modifier.fillMaxWidth()) {
                MenuTile(GIcons.FastForward, "Autoplay next", autoNext, Modifier.weight(1f), onAutoNext)
                MenuTile(GIcons.Timer, if (timerMin > 0) "$timerMin min" else "Timer", timerMin > 0, Modifier.weight(1f), onTimer)
                MenuTile(GIcons.Flip, "Mirror", mirror, Modifier.weight(1f), onMirror)
                MenuTile(GIcons.Share, "Share", false, Modifier.weight(1f)) { onDismiss(); onShare() }
            }
        }
    }
}

/** The playing queue: tap a video to jump to it. */
@Composable
fun QueueSheet(
    queue: List<MediaItem>,
    currentIndex: Int,
    loop: LoopMode,
    shuffle: Boolean,
    onLoop: () -> Unit,
    onShuffle: () -> Unit,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { listState.scrollToItem((currentIndex - 1).coerceAtLeast(0)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Queue", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${queue.size} videos", color = Color(0xFFA8A8AC), fontSize = 12.5.sp)
                }
                GIconButton(
                    GIcons.Repeat, "Loop", onClick = onLoop, size = 40.dp, flat = true,
                    tint = if (loop == LoopMode.OFF) Color.White else cs.primary,
                )
                GIconButton(
                    GIcons.Shuffle, "Shuffle", onClick = onShuffle, size = 40.dp, flat = true,
                    tint = if (shuffle) cs.primary else Color.White,
                )
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(queue, key = { _, v -> v.id }) { i, v ->
                    val current = i == currentIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (current) cs.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .bounceClick { onPick(i) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(width = 84.dp, height = 52.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF222222))) {
                            AsyncImage(
                                model = remember(v.uri) { thumbRequest(ctx, v.uri, 240, true) },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                v.name,
                                color = if (current) cs.primary else Color.White,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            Text(formatDuration(v.durationMs), color = Color(0xFFA8A8AC), fontSize = 12.sp)
                        }
                        if (current) {
                            Icon(GIcons.Play, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun Modifier.fillMaxSize(): Modifier = this.then(Modifier.fillMaxWidth().height(52.dp))
