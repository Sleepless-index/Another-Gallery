package dev.vibe.gallery.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.vibe.gallery.ui.theme.GIcons

/** Fades + slides a card in once, staggered by its position. */
@Composable
private fun StaggerIn(index: Int, content: @Composable () -> Unit) {
    var shown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val delay = (index.coerceAtMost(10)) * 45
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(320, delayMillis = delay)) +
            slideInVertically(tween(360, delayMillis = delay)) { it / 6 },
    ) {
        content()
    }
}

@Composable
private fun SmartTile(icon: ImageVector, title: String, count: String, tint: Color, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(cs.surface)
            .border(BorderStroke(1.dp, cs.outline), shape)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
            Text(count, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlbumTile(name: String, count: Int, cover: dev.vibe.gallery.data.MediaItem, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val request = remember(cover.uri) {
            dev.vibe.gallery.data.thumbRequest(ctx, cover.uri, 480, cover.isVideo)
        }
        AsyncImage(
            model = request,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(cs.surfaceVariant),
        )
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "%,d items".format(count),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

/** The Recent album shows this many of the newest items. */
const val RecentLimit = 200

@Composable
fun AlbumsScreen(
    vm: GalleryViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenSmart: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val albums by vm.albums.collectAsStateWithLifecycle()
    val favorites by vm.favoriteItems.collectAsStateWithLifecycle()
    val allItems by vm.items.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(Unit) { vm.loadTrash() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = "Albums",
            subtitle = "${albums.size} folders",
            trailing = {
                Box {
                    GIconButton(GIcons.More, "More options", onClick = { menu = true })
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        CompactMenuItem(
                            text = { Text("Refresh") },
                            onClick = {
                                menu = false
                                vm.refresh()
                            },
                        )
                        CompactMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                menu = false
                                onOpenSettings()
                            },
                        )
                    }
                }
            },
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp + navBottom),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "smart_fav") {
                StaggerIn(0) {
                    SmartTile(GIcons.HeartFilled, "Favorites", "%,d".format(favorites.size), HeartRed) {
                        onOpenSmart("favorites")
                    }
                }
            }
            item(key = "smart_videos") {
                StaggerIn(1) {
                    SmartTile(GIcons.VideoAlbum, "Videos", "%,d".format(videos.size), cs.onBackground) {
                        onOpenSmart("videos")
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 30) {
                item(key = "smart_trash") {
                    StaggerIn(2) {
                        SmartTile(GIcons.Trash, "Trash", "%,d".format(trashed.size), cs.onBackground) {
                            onOpenSmart("trash")
                        }
                    }
                }
            }
            item(key = "label_all", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "All albums",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp),
                )
            }
            if (allItems.isNotEmpty()) {
                item(key = "album_recent") {
                    StaggerIn(3) {
                        AlbumTile(
                            name = "Recent",
                            count = minOf(allItems.size, RecentLimit),
                            cover = allItems.first(),
                            onClick = { onOpenSmart("recent") },
                        )
                    }
                }
            }
            itemsIndexed(albums, key = { _, a -> "album_${a.bucketId}" }) { index, album ->
                StaggerIn(index + 4) {
                    AlbumTile(
                        name = album.name,
                        count = album.count,
                        cover = album.cover,
                        onClick = { onOpenAlbum(album.bucketId) },
                    )
                }
            }
        }
    }
}
