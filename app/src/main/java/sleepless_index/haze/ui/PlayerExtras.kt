@file:OptIn(ExperimentalMaterial3Api::class)

package sleepless_index.haze.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import sleepless_index.haze.data.LoopMode
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.data.formatDuration
import sleepless_index.haze.data.thumbRequest
import sleepless_index.haze.ui.theme.GIcons

private val Panel = Color(0xFF121212)

/** One row of the player's dropdown menu: icon + label, tinted with the accent when switched on. */
@Composable
fun PlayerMenuRow(icon: ImageVector, label: String, active: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val tint = if (active) cs.primary else LocalContentColor.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = tint, fontSize = 14.sp)
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
                    if (loop == LoopMode.REPEAT) GIcons.RepeatOne else GIcons.Repeat, "Loop", onClick = onLoop, size = 40.dp, flat = true,
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
