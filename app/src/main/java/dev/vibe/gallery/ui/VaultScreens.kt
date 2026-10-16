package dev.vibe.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vibe.gallery.data.MediaItem
import dev.vibe.gallery.ui.theme.GIcons

/** Bottom bar used while selecting inside the Vault. */
@Composable
fun VaultSelectionBar(vm: GalleryViewModel, source: List<MediaItem>, modifier: Modifier = Modifier) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val has = selected.isNotEmpty()
    PillBar(modifier) {
        PillItem(
            icon = GIcons.Unlock, label = "Unhide", selected = false,
            onClick = { if (has) vm.vaultRestore(source.filter { it.id in selected }) },
            modifier = Modifier.weight(1f),
        )
        PillItem(
            icon = GIcons.Trash, label = "Delete", selected = false,
            onClick = { if (has) vm.vaultDelete(source.filter { it.id in selected }) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** Locked state: asks for biometrics / device credential, then shows the vault grid. */
@Composable
fun VaultScreen(
    vm: GalleryViewModel,
    onAuthenticate: (onSuccess: () -> Unit) -> Unit,
    onBack: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit,
) {
    val unlocked by vm.vaultUnlocked.collectAsStateWithLifecycle()
    val items by vm.vault.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    var asked by remember { mutableStateOf(false) }

    LaunchedEffect(unlocked) {
        if (!unlocked && !asked) {
            asked = true
            onAuthenticate { vm.unlockVault() }
        }
    }

    if (unlocked) {
        CollectionScreen(
            vm = vm,
            title = "Vault",
            items = items,
            trash = false,
            onBack = onBack,
            onOpen = onOpen,
            vault = true,
        )
    } else {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(cs.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GIcons.Lock, null, tint = cs.primary, modifier = Modifier.size(38.dp))
            }
            Text(
                "Vault is locked",
                fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                "Files here are hidden from the gallery and other apps.",
                fontSize = 13.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "Unlock",
                fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = cs.onPrimary,
                modifier = Modifier
                    .padding(top = 22.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(cs.primary)
                    .bounceClick(onClick = { onAuthenticate { vm.unlockVault() } })
                    .padding(horizontal = 36.dp, vertical = 14.dp),
            )
            Text(
                "Back",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .bounceClick(onClick = onBack)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
