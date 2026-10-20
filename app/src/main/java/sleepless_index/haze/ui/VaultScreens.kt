package sleepless_index.haze.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.ui.theme.GIcons

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

/** Locked state: asks for the Vault's own PIN, pattern or password, then shows the vault grid. */
@Composable
fun VaultScreen(
    vm: GalleryViewModel,
    onBack: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit,
) {
    val unlocked by vm.vaultUnlocked.collectAsStateWithLifecycle()
    val items by vm.vault.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    var changing by remember { mutableStateOf(false) }
    val stored = prefs.vaultLock

    if (unlocked && !changing) {
        CollectionScreen(
            vm = vm,
            title = "Vault",
            items = items,
            trash = false,
            onBack = onBack,
            onOpen = onOpen,
            vault = true,
            trailingExtra = {
                GIconButton(GIcons.Lock, "Change lock", onClick = { changing = true }, flat = true)
            },
        )
    } else {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            if (stored.isEmpty() || changing) {
                LockSetup(
                    title = if (changing) "Change Vault lock" else "Set a Vault lock",
                    onDone = { type, secret ->
                        vm.setVaultLock(VaultLockStore.make(type, secret))
                        changing = false
                        vm.unlockVault()
                    },
                    onCancel = { if (changing) changing = false else onBack() },
                )
            } else {
                LockEntry(stored = stored, onUnlock = { vm.unlockVault() }, onBack = onBack)
            }
        }
    }
}
