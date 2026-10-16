package dev.vibe.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.vibe.gallery.data.DupGroup
import dev.vibe.gallery.data.MediaItem
import dev.vibe.gallery.data.formatSize
import dev.vibe.gallery.data.thumbRequest
import dev.vibe.gallery.ui.theme.GIcons

@Composable
private fun CleanupRow(title: String, sub: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surface)
            .bounceClick(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
        Text(sub, fontSize = 12.sp, color = cs.onSurfaceVariant)
    }
}

/** Storage cleanup hub: duplicates, large files, screenshots. */
@Composable
fun CleanupScreen(
    vm: GalleryViewModel,
    onBack: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenSmart: (String) -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val large = items.filter { it.sizeBytes >= LargeBytes }
    val shots = items.filter { isScreenshot(it) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = "Storage cleanup",
            subtitle = "%,d items · %s".format(items.size, formatSize(items.sumOf { it.sizeBytes })),
            leading = {
                GIconButton(GIcons.Back, "Back", onClick = onBack)
                Box(Modifier.size(12.dp))
            },
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CleanupRow("Duplicate files", "Find exact copies and free up space", onOpenDuplicates)
            CleanupRow(
                "Large files",
                "%,d files over 50 MB · %s".format(large.size, formatSize(large.sumOf { it.sizeBytes })),
            ) { onOpenSmart("large") }
            CleanupRow(
                "Screenshots",
                "%,d screenshots · %s".format(shots.size, formatSize(shots.sumOf { it.sizeBytes })),
            ) { onOpenSmart("screenshots") }
        }
    }
}

const val LargeBytes = 50L * 1024 * 1024

fun isScreenshot(m: MediaItem): Boolean =
    m.relativePath.contains("screenshot", ignoreCase = true) ||
        m.bucketName.contains("screenshot", ignoreCase = true) ||
        m.name.startsWith("Screenshot", ignoreCase = true)

@Composable
fun DuplicatesScreen(
    vm: GalleryViewModel,
    onBack: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit,
) {
    val state by vm.dups.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    LaunchedEffect(state) { if (state is GalleryViewModel.DupState.Idle) vm.scanDuplicates() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val groups = (state as? GalleryViewModel.DupState.Done)?.groups.orEmpty()
        ScreenHeader(
            title = "Duplicates",
            subtitle = if (state is GalleryViewModel.DupState.Done)
                "${groups.size} groups · ${formatSize(groups.sumOf { it.wastedBytes })} to free" else "Scanning…",
            leading = {
                GIconButton(GIcons.Back, "Back", onClick = onBack)
                Box(Modifier.size(12.dp))
            },
        )
        when (val st = state) {
            is GalleryViewModel.DupState.Done -> {
                if (groups.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No duplicates found", color = cs.onSurfaceVariant, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 40.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(groups, key = { g -> g.items.first().id }) { g ->
                            GroupCard(g, vm, ctx, onOpen)
                        }
                    }
                }
            }
            else -> {
                val (d, t) = (st as? GalleryViewModel.DupState.Scanning)?.let { it.done to it.total } ?: (0 to 0)
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = cs.primary, strokeWidth = 3.dp)
                    Text(
                        if (t > 0) "Comparing files $d / $t" else "Looking for candidates…",
                        color = cs.onSurfaceVariant, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: DupGroup,
    vm: GalleryViewModel,
    ctx: android.content.Context,
    onOpen: (List<MediaItem>, Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    group.items.first().name, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${group.items.size} copies · ${formatSize(group.wastedBytes)} wasted",
                    fontSize = 12.sp, color = cs.onSurfaceVariant,
                )
            }
            Text(
                "Delete extras",
                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = cs.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.primary)
                    .bounceClick(onClick = { vm.requestTrash(group.items.drop(1)) })
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(group.items, key = { it.id }) { m ->
                Column(Modifier.width(96.dp).bounceClick(onClick = { onOpen(group.items, group.items.indexOf(m)) })) {
                    AsyncImage(
                        model = remember(m.uri) { thumbRequest(ctx, m.uri, 240, m.isVideo) },
                        contentDescription = m.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceVariant),
                    )
                    Text(
                        if (m == group.items.first()) "Keep · ${m.bucketName}" else m.bucketName,
                        fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
