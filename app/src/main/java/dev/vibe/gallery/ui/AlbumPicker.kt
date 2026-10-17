package dev.vibe.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vibe.gallery.data.MediaItem
import dev.vibe.gallery.data.MediaOps
import dev.vibe.gallery.ui.theme.GIcons

/** Pick (or create) an album to copy or move items into. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPickerSheet(
    vm: GalleryViewModel,
    items: List<MediaItem>,
    initialMove: Boolean,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val albums by vm.albums.collectAsStateWithLifecycle()
    var move by remember { mutableStateOf(initialMove) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val roots = remember { MediaOps.storageRoots(context) }
    var rootIdx by remember { mutableStateOf(0) }
    val allFiles = remember { MediaOps.hasAllFiles() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface,
        contentColor = cs.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (move) "Move ${items.size} to…" else "Copy ${items.size} to…",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("Move", move) { move = true }
                ModeChip("Copy", !move) { move = false }
            }
            if (!allFiles) {
                Text(
                    "Turn on All files access in Settings to move without prompts and to use the SD card.",
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item(key = "new") {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { creating = !creating },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(GIcons.Plus, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
                            Text("New album", fontWeight = FontWeight.Bold, color = cs.primary)
                        }
                        if (creating && roots.size > 1) {
                            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                roots.forEachIndexed { i, r -> ModeChip(r.first, rootIdx == i) { rootIdx = i } }
                            }
                        }
                        if (creating) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = newName,
                                    onValueChange = { newName = it.take(40) },
                                    singleLine = true,
                                    label = { Text("Album name") },
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    enabled = newName.isNotBlank(),
                                    onClick = {
                                        vm.transferToNew(items, roots[rootIdx].second, newName, move)
                                        onDismiss()
                                    },
                                ) { Text("Create", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
                items(albums, key = { it.bucketId }) { album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                vm.transferToAlbum(items, album, move)
                                onDismiss()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            album.name,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Text("%,d".format(album.count), fontSize = 13.sp, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, active: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) cs.primary else cs.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (active) cs.onPrimary else cs.onBackground,
        )
    }
}
