package dev.vibe.gallery.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.vibe.gallery.data.Entry
import dev.vibe.gallery.data.LayoutMode
import dev.vibe.gallery.data.MediaItem
import dev.vibe.gallery.data.formatDuration
import dev.vibe.gallery.data.monthLabel
import dev.vibe.gallery.ui.theme.GIcons
import dev.vibe.gallery.util.shareItems
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DateHeader(entry: Entry.Header, horizontalPad: Dp, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = horizontalPad, end = horizontalPad, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = entry.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (entry.sub.isNotEmpty()) {
            Text(
                text = entry.sub,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaTile(
    item: MediaItem,
    isFav: Boolean,
    selectMode: Boolean,
    selected: Boolean,
    baseCorner: Dp,
    thumbPx: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme

    val scale by animateFloatAsState(
        targetValue = if (selected) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "tileScale",
    )
    val corner by animateDpAsState(
        targetValue = if (selected) maxOf(baseCorner, 14.dp) else baseCorner,
        animationSpec = tween(180),
        label = "tileCorner",
    )
    val dim by animateFloatAsState(if (selected) 0.38f else 0f, tween(180), label = "tileDim")

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(Color(0xFF1A1A1A))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val request = remember(item.uri, thumbPx) {
            ImageRequest.Builder(ctx).data(item.uri).size(thumbPx).build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (dim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim))
            )
        }

        if (item.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(start = 5.dp, end = 7.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(GIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                Text(
                    text = formatDuration(item.durationMs),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        AnimatedVisibility(
            visible = isFav,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(7.dp),
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Icon(GIcons.HeartFilled, contentDescription = null, tint = cs.primary, modifier = Modifier.size(14.dp))
        }

        AnimatedVisibility(
            visible = selectMode,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            val fill by animateColorAsState(
                targetValue = if (selected) cs.primary else Color.Black.copy(alpha = 0.28f),
                animationSpec = tween(160),
                label = "boxFill",
            )
            val boxShape = RoundedCornerShape(6.dp)
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(boxShape)
                    .background(fill)
                    .border(
                        BorderStroke(1.5.dp, if (selected) cs.primary else Color.White.copy(alpha = 0.85f)),
                        boxShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = selected,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    Icon(GIcons.Check, contentDescription = null, tint = cs.onPrimary, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

/** Thin draggable scrollbar with a month bubble. Appears while scrolling, fades out after. */
@Composable
fun FastScroller(
    totalCount: Int,
    firstVisibleIndex: Int,
    isScrolling: Boolean,
    labelAt: (Int) -> String,
    scrollTo: suspend (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var trackHeight by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    val visible = (isScrolling || dragging) && totalCount > 60
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 120 else 700),
        label = "fastScrollAlpha",
    )
    if (alpha <= 0.01f && !visible) return

    val frac = if (dragging) {
        dragFraction
    } else if (totalCount > 1) {
        (firstVisibleIndex.toFloat() / (totalCount - 1)).coerceIn(0f, 1f)
    } else {
        0f
    }
    val thumbHeightPx = with(LocalDensity.current) { 48.dp.toPx() }
    val thumbY = ((trackHeight - thumbHeightPx) * frac).roundToInt().coerceAtLeast(0)

    val dragModifier = if (visible) {
        Modifier.pointerInput(totalCount) {
            detectVerticalDragGestures(
                onDragStart = { start ->
                    dragging = true
                    dragFraction = (start.y / size.height).coerceIn(0f, 1f)
                },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false },
                onVerticalDrag = { change, _ ->
                    change.consume()
                    dragFraction = (change.position.y / size.height).coerceIn(0f, 1f)
                    val target = (dragFraction * (totalCount - 1)).roundToInt()
                    scope.launch { scrollTo(target) }
                },
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(36.dp)
            .alpha(alpha)
            .onSizeChanged { trackHeight = it.height }
            .then(dragModifier),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbY) }
                .padding(end = 4.dp)
                .size(width = 5.dp, height = 48.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(cs.primary)
        )
        AnimatedVisibility(
            visible = dragging,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbY + (thumbHeightPx / 2f).roundToInt() - 14.dp.roundToPx()) }
                .padding(end = 18.dp),
            enter = fadeIn(tween(100)) + scaleIn(tween(120), initialScale = 0.8f),
            exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.8f),
        ) {
            val idx = (dragFraction * (totalCount - 1).coerceAtLeast(0)).roundToInt()
            Text(
                text = labelAt(idx),
                color = cs.onPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.primary)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun MediaCollection(
    entries: List<Entry>,
    layout: LayoutMode,
    columns: Int,
    selected: Set<Long>,
    selectMode: Boolean,
    favorites: Set<Long>,
    onClick: (MediaItem) -> Unit,
    onLongClick: (MediaItem) -> Unit,
    bottomPad: Dp,
    modifier: Modifier = Modifier,
    emptyText: String = "Nothing here yet",
) {
    if (entries.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        return
    }

    val gridState = rememberLazyGridState()
    val staggeredState = rememberLazyStaggeredGridState()
    val labelAt = remember(entries) { { idx: Int -> monthLabel(entries.getOrNull(idx)?.dateMs ?: 0L) } }
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val gridThumb = (screenWidthPx / columns).toInt().coerceIn(128, 512)
    val masonryThumb = (screenWidthPx / 2f).toInt().coerceIn(200, 640)

    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = layout,
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
            label = "layoutSwitch",
        ) { mode ->
            Box(Modifier.fillMaxSize()) {
                if (mode == LayoutMode.GRID) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(bottom = bottomPad),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = entries,
                            key = { it.key },
                            span = { e ->
                                if (e is Entry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                            },
                            contentType = { it::class },
                        ) { e ->
                            when (e) {
                                is Entry.Header -> DateHeader(
                                    entry = e,
                                    horizontalPad = 20.dp,
                                    modifier = Modifier.animateItem(),
                                )
                                is Entry.Media -> MediaTile(
                                    item = e.item,
                                    isFav = e.item.id in favorites,
                                    selectMode = selectMode,
                                    selected = e.item.id in selected,
                                    baseCorner = 0.dp,
                                    thumbPx = gridThumb,
                                    onClick = { onClick(e.item) },
                                    onLongClick = { onLongClick(e.item) },
                                    modifier = Modifier
                                        .animateItem()
                                        .aspectRatio(1f),
                                )
                            }
                        }
                    }
                    FastScroller(
                        totalCount = gridState.layoutInfo.totalItemsCount,
                        firstVisibleIndex = gridState.firstVisibleItemIndex,
                        isScrolling = gridState.isScrollInProgress,
                        labelAt = labelAt,
                        scrollTo = { gridState.scrollToItem(it) },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                } else {
                    LazyVerticalStaggeredGrid(
                        state = staggeredState,
                        columns = StaggeredGridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = bottomPad),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalItemSpacing = 6.dp,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = entries,
                            key = { it.key },
                            contentType = { it::class },
                            span = { e ->
                                if (e is Entry.Header || (e is Entry.Media && e.item.aspect >= 1.15f)) {
                                    StaggeredGridItemSpan.FullLine
                                } else {
                                    StaggeredGridItemSpan.SingleLane
                                }
                            },
                        ) { e ->
                            when (e) {
                                is Entry.Header -> DateHeader(
                                    entry = e,
                                    horizontalPad = 14.dp,
                                    modifier = Modifier.animateItem(),
                                )
                                is Entry.Media -> MediaTile(
                                    item = e.item,
                                    isFav = e.item.id in favorites,
                                    selectMode = selectMode,
                                    selected = e.item.id in selected,
                                    baseCorner = 16.dp,
                                    thumbPx = masonryThumb,
                                    onClick = { onClick(e.item) },
                                    onLongClick = { onLongClick(e.item) },
                                    modifier = Modifier
                                        .animateItem()
                                        .fillMaxWidth()
                                        .aspectRatio(e.item.aspect),
                                )
                            }
                        }
                    }
                    FastScroller(
                        totalCount = staggeredState.layoutInfo.totalItemsCount,
                        firstVisibleIndex = staggeredState.firstVisibleItemIndex,
                        isScrolling = staggeredState.isScrollInProgress,
                        labelAt = labelAt,
                        scrollTo = { staggeredState.scrollToItem(it) },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}

/** Bottom action bar shown while selecting. */
@Composable
fun SelectionBar(
    vm: GalleryViewModel,
    source: List<MediaItem>,
    trash: Boolean,
    modifier: Modifier = Modifier,
) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val has = selected.isNotEmpty()

    PillBar(modifier) {
        if (!trash) {
            val allFav = has && selected.all { it in favs }
            PillItem(
                icon = GIcons.Share, label = "Share", selected = false,
                onClick = { if (has) shareItems(context, source.filter { it.id in selected }) },
                modifier = Modifier.weight(1f),
            )
            PillItem(
                icon = if (allFav) GIcons.HeartFilled else GIcons.Heart,
                label = if (allFav) "Unfavorite" else "Favorite",
                selected = allFav,
                onClick = { if (has) vm.toggleFavorite(selected) },
                modifier = Modifier.weight(1f),
            )
            PillItem(
                icon = GIcons.Trash, label = "Delete", selected = false,
                onClick = { if (has) vm.requestTrash(source.filter { it.id in selected }) },
                modifier = Modifier.weight(1f),
            )
        } else {
            PillItem(
                icon = GIcons.Restore, label = "Restore", selected = false,
                onClick = { if (has) vm.requestRestore(source.filter { it.id in selected }) },
                modifier = Modifier.weight(1f),
            )
            PillItem(
                icon = GIcons.Trash, label = "Delete forever", selected = false,
                onClick = { if (has) vm.requestDeleteForever(source.filter { it.id in selected }) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
